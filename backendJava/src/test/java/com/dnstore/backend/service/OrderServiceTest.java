package com.dnstore.backend.service;

import com.dnstore.backend.model.*;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.exception.StaleShippingQuoteException;
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

        @Mock
        private StockLedger stockLedger;

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
                verify(stockLedger).recordSale(any(Order.class));
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
        void quoteShipping_catalogPackageMeasurements_shouldMapHeightWidthDepthAndWeightToShippingItem() {
                // height, width, depth (sent as length), weight: the registered package measurements.
                double[][] measurements = {
                                { 12, 20, 25, 0.20 }, // Boné
                                { 4, 22, 30, 0.25 }, // Camisa de ciclismo
                                { 3, 22, 28, 0.20 }, // Camisa de poliamida
                                { 3, 10, 20, 0.08 } // Meia
                };

                for (double[] m : measurements) {
                        PhysicalProduct product = physicalProduct();
                        product.setHeight(m[0]);
                        product.setWidth(m[1]);
                        product.setDepth(m[2]);
                        product.setWeight(m[3]);
                        clearInvocations(deliveryService);

                        orderService.quoteShipping(user, "12500-000");

                        verify(deliveryService).calculateOptions(eq("12500000"), eq(List.of(new ShippingItem(
                                        variant.getId().toString(),
                                        BigDecimal.valueOf(m[3]), BigDecimal.valueOf(m[1]),
                                        BigDecimal.valueOf(m[0]), BigDecimal.valueOf(m[2]),
                                        new BigDecimal("100.00"), 2))));
                }
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
                                .isInstanceOf(StaleShippingQuoteException.class)
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
                                .isInstanceOf(StaleShippingQuoteException.class)
                                .hasMessageContaining("opção de frete expirou");
        }

        @Test
        void checkout_rejectsExpiredQuoteWithStaleCodeAndNeverCreatesOrder() {
                UUID quoteOptionId = orderService.quoteShipping(user, address.getZipCode()).get(0).id();
                shippingOptions.get(quoteOptionId).setExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1));

                assertThatThrownBy(() -> orderService.checkout(
                                user, null, quoteOptionId, null, address.getId()))
                                .isInstanceOf(StaleShippingQuoteException.class);

                verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void checkout_withoutShippingOptionAsksForNewQuote() {
                assertThatThrownBy(() -> orderService.checkout(user, null, null, null, address.getId()))
                                .isInstanceOf(StaleShippingQuoteException.class);
        }

        @Test
        void checkout_afterStaleQuoteCustomerCanQuoteAgainAndCompleteWithServerSidePrice() {
                UUID expired = orderService.quoteShipping(user, address.getZipCode()).get(0).id();
                shippingOptions.get(expired).setExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1));
                assertThatThrownBy(() -> orderService.checkout(user, null, expired, null, address.getId()))
                                .isInstanceOf(StaleShippingQuoteException.class);

                UUID fresh = orderService.quoteShipping(user, address.getZipCode()).get(0).id();
                Order order = orderService.checkout(user, null, fresh, null, address.getId());

                assertThat(fresh).isNotEqualTo(expired);
                assertThat(order.getShippingCost()).isEqualByComparingTo("10.00");
                assertThat(order.getTotal()).isEqualByComparingTo("210.00");
        }

        @Test
        void quoteShipping_recentQuoteForSameCartAndZipIsReusedWithoutCallingTheProvider() {
                ShippingQuoteOptionEntity recent = new ShippingQuoteOptionEntity();
                recent.setId(UUID.randomUUID());
                recent.setServiceId("1:123");
                recent.setServiceName("Correios PAC");
                recent.setPrice(new BigDecimal("10.00"));
                recent.setDeliveryDays(3);
                recent.setSource("MELHOR_ENVIO");
                recent.setQuotedAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1));
                when(shippingQuoteOptionRepository.findReusable(
                                eq(user.getId()), anyString(), eq("12500000"), eq("12600000"), eq("sandbox"),
                                any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of(recent));

                List<OrderService.ShippingOption> options = orderService.quoteShipping(user, "12500-000");

                assertThat(options).singleElement().satisfies(option -> {
                        assertThat(option.id()).isEqualTo(recent.getId());
                        assertThat(option.cost()).isEqualByComparingTo("10.00");
                });
                verify(deliveryService, never()).calculateOptions(anyString(), anyList());
                verify(shippingQuoteOptionRepository, never()).save(any(ShippingQuoteOptionEntity.class));
        }

        @Test
        void quoteShipping_reuseOnlyReturnsOptionsFromTheLatestProviderQuery() {
                LocalDateTime latestAt = LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1);
                ShippingQuoteOptionEntity latest = reusableOption("Correios PAC", latestAt);
                ShippingQuoteOptionEntity older = reusableOption("Jadlog .Package", latestAt.minusMinutes(2));
                when(shippingQuoteOptionRepository.findReusable(
                                any(UUID.class), anyString(), anyString(), anyString(), anyString(),
                                any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of(latest, older));

                assertThat(orderService.quoteShipping(user, "12500-000"))
                                .extracting(OrderService.ShippingOption::typeName)
                                .containsExactly("Correios PAC");
        }

        @Test
        void quoteShipping_whenNothingReusableCallsTheProviderAndPersistsOnce() {
                orderService.quoteShipping(user, "12500-000");

                verify(deliveryService, times(1)).calculateOptions(anyString(), anyList());
                verify(shippingQuoteOptionRepository, times(1)).save(any(ShippingQuoteOptionEntity.class));
        }

        private ShippingQuoteOptionEntity reusableOption(String name, LocalDateTime quotedAt) {
                ShippingQuoteOptionEntity option = new ShippingQuoteOptionEntity();
                option.setId(UUID.randomUUID());
                option.setServiceId(name);
                option.setServiceName(name);
                option.setPrice(new BigDecimal("10.00"));
                option.setDeliveryDays(3);
                option.setSource("MELHOR_ENVIO");
                option.setQuotedAt(quotedAt);
                return option;
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

        private Coupon percentageCoupon() {
                Coupon coupon = new Coupon();
                coupon.setId(UUID.randomUUID());
                coupon.setCode("DN10");
                coupon.setActive(true);
                coupon.setDiscountPercentage(new BigDecimal("10"));
                return coupon;
        }

        @Test
        void checkout_withCoupon_shouldConsumeUsageAtomicallyAndApplyDiscount() {
                Coupon coupon = percentageCoupon();
                when(couponService.validateCoupon("DN10")).thenReturn(Optional.of(coupon));
                UUID shippingOptionId = orderService.quoteShipping(user, address.getZipCode()).get(0).id();

                Order order = orderService.checkout(user, null, shippingOptionId, "DN10", address.getId());

                // 2 x 100,00 - 10% + 10,00 de frete
                assertThat(order.getTotal()).isEqualByComparingTo("190.00");
                assertThat(order.getDiscountAmount()).isEqualByComparingTo("20.00");
                assertThat(order.getCoupon()).isSameAs(coupon);
                verify(couponService).registerUsage(coupon);
                // Nunca mais "ler, incrementar e gravar" o cupom: o contador só muda pelo UPDATE atômico.
                verify(couponRepository, never()).save(any(Coupon.class));
        }

        @Test
        void checkout_whenCouponLimitWasReached_shouldFailBeforeTouchingStockOrOrder() {
                Coupon coupon = percentageCoupon();
                when(couponService.validateCoupon("DN10")).thenReturn(Optional.of(coupon));
                org.mockito.Mockito.doThrow(new IllegalArgumentException("Este cupom já atingiu o limite máximo de uso."))
                                .when(couponService).registerUsage(coupon);
                UUID shippingOptionId = orderService.quoteShipping(user, address.getZipCode()).get(0).id();

                assertThatThrownBy(() -> orderService.checkout(user, null, shippingOptionId, "DN10", address.getId()))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("limite máximo de uso");

                assertThat(variant.getStock()).isEqualTo(5);
                verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void updateStatus_markingPaidThroughTheGenericEndpoint_shouldBeRejected() {
                assertThatThrownBy(() -> orderService.updateStatus(UUID.randomUUID(), "PAID"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("confirmação manual de pagamento");

                verifyNoInteractions(orderRepository);
        }

        @Test
        void updateStatus_cancelledOrderWithReleasedStock_cannotGoBackToLogistics() {
                Order order = new Order();
                order.setId(UUID.randomUUID());
                order.setStatus("CANCELLED");
                order.setStockReleased(true);
                when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

                assertThatThrownBy(() -> orderService.updateStatus(order.getId(), "PROCESSING"))
                                .isInstanceOf(ConflictException.class)
                                .hasMessageContaining("estoque deste pedido já foi devolvido");

                assertThat(order.getStatus()).isEqualTo("CANCELLED");
                verify(orderRepository, never()).save(order);
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
