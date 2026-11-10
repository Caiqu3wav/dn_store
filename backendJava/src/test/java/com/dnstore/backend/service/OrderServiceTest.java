package com.dnstore.backend.service;

import com.dnstore.backend.model.*;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.repository.*;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem;
import com.dnstore.backend.service.shipping.ShippingQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

        @Mock
        private DeliveryService deliveryService;

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private CartRepository cartRepository;

        @Mock
        private AddressRepository addressRepository;

        @Mock
        private CouponService couponService;

        @Mock
        private CouponRepository couponRepository;

        @Mock
        private ProductVariantRepository productVariantRepository;

        @Mock
        private OrderInventoryService orderInventoryService;

        @Mock
        private PaymentRepository paymentRepository;

        @Mock
        private ShippingQuoteOptionRepository shippingQuoteOptionRepository;

        @Mock
        private MelhorEnvioProperties melhorEnvioProperties;

        @InjectMocks
        private OrderService orderService;

        private User user;
        private Address address;
        private Cart cart;
        private ProductVariant variant;
        private final Map<UUID, ShippingQuoteOptionEntity> shippingOptions = new java.util.HashMap<>();

        @BeforeEach
        void setUp() {
                user = new User();
                user.setId(UUID.randomUUID());
                user.setEmail("user@email.com");
                user.setName("Test User");
                user.setRole(Role.USER);

                address = new Address();
                address.setId(UUID.randomUUID());
                address.setUser(user);
                address.setStreet("Rua A");
                address.setNumber("123");
                address.setNeighborhood("Centro");
                address.setCity("São Paulo");
                address.setState("SP");
                address.setZipCode("01000-000");

                cart = new Cart();
                cart.setUser(user);
                cart.setItems(new ArrayList<>());

                PhysicalProduct product = new PhysicalProduct();
                product.setId(UUID.randomUUID());
                product.setName("Camiseta");
                product.setPrice(new BigDecimal("100.00"));
                product.setWeight(0.5);
                product.setWidth(20);
                product.setHeight(10);
                product.setDepth(30);
                product.setStock(5);

                variant = new ProductVariant();
                variant.setId(UUID.randomUUID());
                variant.setProduct(product);
                variant.setStock(5);
                variant.setSize("M");

                CartItem cartItem = new CartItem();
                cartItem.setCart(cart);
                cartItem.setProductVariant(variant);
                cartItem.setQuantity(2);
                cart.getItems().add(cartItem);

                lenient().when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
                lenient().when(cartRepository.findByUserForUpdate(user)).thenReturn(Optional.of(cart));
                lenient().when(deliveryService.normalizedOriginZip()).thenReturn("12600000");
                ReflectionTestUtils.setField(orderService, "shippingGateway", "melhorEnvio");
                lenient().when(deliveryService.calculateOptions(anyString(), anyList()))
                                .thenReturn(List.of(new ShippingQuote(
                                        "1:123", "Correios PAC", new BigDecimal("10.00"), 3, "MELHOR_ENVIO")));
                lenient().when(melhorEnvioProperties.getEnvironment()).thenReturn("sandbox");
                lenient().when(melhorEnvioProperties.getQuoteTtl()).thenReturn(Duration.ofMinutes(15));
                lenient().when(shippingQuoteOptionRepository.save(any(ShippingQuoteOptionEntity.class)))
                                .thenAnswer(invocation -> {
                                        ShippingQuoteOptionEntity option = invocation.getArgument(0);
                                        option.setId(UUID.randomUUID());
                                        shippingOptions.put(option.getId(), option);
                                        return option;
                                });
                lenient().when(shippingQuoteOptionRepository.findByIdAndUser_IdAndExpiresAtAfter(
                                any(UUID.class), eq(user.getId()), any(LocalDateTime.class)))
                                .thenAnswer(invocation -> {
                                        ShippingQuoteOptionEntity option = shippingOptions.get(invocation.getArgument(0));
                                        UUID ownerId = invocation.getArgument(1);
                                        LocalDateTime requestedAt = invocation.getArgument(2);
                                        return option != null
                                                && option.getUser().getId().equals(ownerId)
                                                && option.getExpiresAt().isAfter(requestedAt)
                                                        ? Optional.of(option)
                                                        : Optional.empty();
                                });
                lenient().when(addressRepository.findByIdAndUser_Id(address.getId(), user.getId()))
                                .thenReturn(Optional.of(address));
                lenient().when(orderRepository.save(any(Order.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                lenient().when(productVariantRepository.findByIdForUpdate(any(UUID.class)))
                                .thenReturn(Optional.of(variant));
                lenient().when(productVariantRepository.save(any(ProductVariant.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        void checkout_validOrder_shouldTakeSnapshotOfUnitPriceAndDecreaseStock() {
                UUID shippingOptionId = orderService.quoteShipping(user, address.getZipCode()).get(0).id();
                Order order = orderService.checkout(user, null, shippingOptionId, null, address.getId());

                assertThat(order.getTotal()).isEqualByComparingTo("210.00");
                assertThat(order.getShippingCost()).isEqualByComparingTo("10.00");
                assertThat(order.getShippingType()).isEqualTo("Correios PAC");
                assertThat(order.getShippingServiceId()).isEqualTo("1:123");
                assertThat(order.getShippingQuoteOptionId()).isEqualTo(shippingOptionId);
                assertThat(order.getShippingDeadlineDays()).isEqualTo(3);
                assertThat(order.getItems()).hasSize(1);
                assertThat(order.getItems().get(0).getPrice()).isEqualByComparingTo("100.00");
                assertThat(variant.getStock()).isEqualTo(3);
                verify(productVariantRepository, times(1)).save(variant);
                verify(deliveryService).calculateOptions(eq("01000000"), eq(List.of(new ShippingItem(
                                variant.getId().toString(),
                                new BigDecimal("0.5"), new BigDecimal("20.0"), new BigDecimal("10.0"),
                                new BigDecimal("30.0"), new BigDecimal("100.00"), 2))));
        }

        @Test
        void quoteShipping_validPhysicalProduct_shouldCreateShippingItemFromPersistedValues() {
                List<OrderService.ShippingOption> options = orderService.quoteShipping(user, "12500-000");

                assertThat(options).hasSize(1);
                assertThat(options.get(0).serviceId()).isEqualTo("1:123");
                assertThat(options.get(0).typeName()).isEqualTo("Correios PAC");
                assertThat(options.get(0).cost()).isEqualByComparingTo("10.00");
                assertThat(options.get(0).source()).isEqualTo("MELHOR_ENVIO");
                verify(deliveryService).calculateOptions(eq("12500000"), eq(List.of(new ShippingItem(
                                variant.getId().toString(),
                                new BigDecimal("0.5"), new BigDecimal("20.0"), new BigDecimal("10.0"),
                                new BigDecimal("30.0"), new BigDecimal("100.00"), 2))));
        }

        @Test
        void quoteShipping_zeroWeight_shouldRejectProduct() {
                physicalProduct().setWeight(0);

                assertInvalidShippingProduct();
        }

        @Test
        void checkout_rejectsQuoteWhenCartChangedAfterCustomerAcceptedIt() {
                UUID quoteOptionId = orderService.quoteShipping(user, address.getZipCode()).get(0).id();
                cart.getItems().get(0).setQuantity(3);

                assertThatThrownBy(() -> orderService.checkout(
                                user, null, quoteOptionId, null, address.getId()))
                                .isInstanceOf(ConflictException.class)
                                .hasMessageContaining("dados do carrinho");

                verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void checkout_rejectsQuoteThatBelongsToAnotherCustomer() {
                UUID quoteOptionId = orderService.quoteShipping(user, address.getZipCode()).get(0).id();
                User anotherUser = new User();
                anotherUser.setId(UUID.randomUUID());
                when(cartRepository.findByUserForUpdate(anotherUser)).thenReturn(Optional.of(cart));

                assertThatThrownBy(() -> orderService.checkout(
                                anotherUser,
                                new OrderService.AddressData(
                                        "Rua A", "123", null, "Centro", "São Paulo", "SP", address.getZipCode()),
                                quoteOptionId,
                                null,
                                null))
                                .isInstanceOf(ConflictException.class)
                                .hasMessageContaining("opção de frete expirou");
        }

        @Test
        void quoteShipping_zeroDimension_shouldRejectProduct() {
                physicalProduct().setWidth(0);

                assertInvalidShippingProduct();
        }

        @Test
        void quoteShipping_zeroHeight_shouldRejectProduct() {
                physicalProduct().setHeight(0);

                assertInvalidShippingProduct();
        }

        @Test
        void quoteShipping_zeroDepth_shouldRejectProduct() {
                physicalProduct().setDepth(0);

                assertInvalidShippingProduct();
        }

        @Test
        void quoteShipping_zeroQuantity_shouldRejectProduct() {
                cart.getItems().get(0).setQuantity(0);

                assertInvalidShippingProduct();
        }

        @Test
        void quoteShipping_nonPhysicalProduct_shouldRejectProduct() {
                variant.setProduct(mock(Product.class));

                assertInvalidShippingProduct();
        }

        @Test
        void quoteShipping_nonFiniteWeight_shouldRejectProduct() {
                physicalProduct().setWeight(Double.NaN);

                assertInvalidShippingProduct();
        }

        @Test
        void checkout_whenCartIsEmpty_shouldFail() {
                cart.getItems().clear();

                assertThatThrownBy(() -> orderService.checkout(user, null, null, null, address.getId()))
                                .isInstanceOf(ConflictException.class)
                                .hasMessageContaining("vazio");
        }

        @Test
        void checkout_whenStockIsInsufficient_shouldFailBeforePersistingOrder() {
                CartItem cartItem = cart.getItems().get(0);
                cartItem.setQuantity(10);

                assertThatThrownBy(() -> orderService.checkout(user, null, null, null, address.getId()))
                                .isInstanceOf(ConflictException.class)
                                .hasMessageContaining("Estoque insuficiente");

                verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void checkout_whenStockIsZero_shouldFailBeforePersistingOrder() {
                variant.setStock(0);

                assertThatThrownBy(() -> orderService.checkout(user, null, null, null, address.getId()))
                                .isInstanceOf(ConflictException.class)
                                .hasMessageContaining("Estoque insuficiente");

                verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void checkout_whenAddressDoesNotBelongToUser_shouldFail() {
                when(addressRepository.findByIdAndUser_Id(address.getId(), user.getId())).thenReturn(Optional.empty());

                assertThatThrownBy(() -> orderService.checkout(user, null, null, null, address.getId()))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Endereço não encontrado");
        }

        @Test
        void updateStatus_cancellingWithActivePayment_shouldKeepReservedStock() {
                Order order = new Order();
                order.setId(UUID.randomUUID());
                order.setStatus("PENDING_PAYMENT");
                Payment payment = new Payment();
                payment.setStatus("PENDING");
                when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
                when(paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(order.getId()))
                                .thenReturn(List.of(payment));

                assertThatThrownBy(() -> orderService.updateStatus(order.getId(), "CANCELLED"))
                                .isInstanceOf(ConflictException.class)
                                .hasMessageContaining("pagamento não reconciliada");

                assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT");
                verify(orderInventoryService, never()).releaseReservedStock(order);
                verify(orderRepository, never()).save(order);
        }

        @Test
        void updateStatus_cancellingWithoutPaymentAttempts_shouldReleaseReservedStock() {
                Order order = new Order();
                order.setId(UUID.randomUUID());
                order.setStatus("PENDING_PAYMENT");
                when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
                when(paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(order.getId())).thenReturn(List.of());
                when(orderRepository.save(order)).thenReturn(order);

                assertThat(orderService.updateStatus(order.getId(), "CANCELLED")).contains(order);

                assertThat(order.getStatus()).isEqualTo("CANCELLED");
                verify(orderInventoryService).releaseReservedStock(order);
        }

        @Test
        void updateStatus_cancellingFailedPaymentOrder_shouldReleaseReservedStock() {
                Order order = new Order();
                order.setId(UUID.randomUUID());
                order.setStatus("PAYMENT_FAILED");
                Payment payment = new Payment();
                payment.setStatus("FAILED");
                when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
                when(paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(order.getId()))
                                .thenReturn(List.of(payment));
                when(orderRepository.save(order)).thenReturn(order);

                assertThat(orderService.updateStatus(order.getId(), "CANCELLED")).contains(order);

                assertThat(order.getStatus()).isEqualTo("CANCELLED");
                verify(orderInventoryService).releaseReservedStock(order);
        }

        private PhysicalProduct physicalProduct() {
                return (PhysicalProduct) variant.getProduct();
        }

        private void assertInvalidShippingProduct() {
                assertThatThrownBy(() -> orderService.quoteShipping(user, "12500-000"))
                                .isInstanceOf(IllegalArgumentException.class);

                verifyNoInteractions(deliveryService);
        }
}
