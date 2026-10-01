package com.dnstore.backend.service;

import com.dnstore.backend.model.*;
import com.dnstore.backend.model.enums.Role;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

        @InjectMocks
        private OrderService orderService;

        private User user;
        private Address address;
        private Cart cart;
        private ProductVariant variant;

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
                lenient().when(deliveryService.calculateShipping(anyString(), anyList(), eq("PAC")))
                                .thenReturn(new ShippingQuote("PAC", new BigDecimal("10.00"), 3));
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
                Order order = orderService.checkout(user, null, "PAC", null, address.getId());

                assertThat(order.getTotal()).isEqualByComparingTo("210.00");
                assertThat(order.getShippingCost()).isEqualByComparingTo("10.00");
                assertThat(order.getShippingType()).isEqualTo("PAC");
                assertThat(order.getShippingDeadlineDays()).isEqualTo(3);
                assertThat(order.getItems()).hasSize(1);
                assertThat(order.getItems().get(0).getPrice()).isEqualByComparingTo("100.00");
                assertThat(variant.getStock()).isEqualTo(3);
                verify(productVariantRepository, times(1)).save(variant);
                verify(deliveryService).calculateShipping(eq("01000-000"), eq(List.of(new ShippingItem(
                                new BigDecimal("0.5"), new BigDecimal("20.0"), new BigDecimal("10.0"),
                                new BigDecimal("30.0"), 2))), eq("PAC"));
        }

        @Test
        void checkout_whenCartIsEmpty_shouldFail() {
                cart.getItems().clear();

                assertThatThrownBy(() -> orderService.checkout(user, null, "PAC", null, address.getId()))
                                .isInstanceOf(ConflictException.class)
                                .hasMessageContaining("vazio");
        }

        @Test
        void checkout_whenStockIsInsufficient_shouldFailBeforePersistingOrder() {
                CartItem cartItem = cart.getItems().get(0);
                cartItem.setQuantity(10);

                assertThatThrownBy(() -> orderService.checkout(user, null, "PAC", null, address.getId()))
                                .isInstanceOf(ConflictException.class)
                                .hasMessageContaining("Estoque insuficiente");

                verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void checkout_whenAddressDoesNotBelongToUser_shouldFail() {
                when(addressRepository.findByIdAndUser_Id(address.getId(), user.getId())).thenReturn(Optional.empty());

                assertThatThrownBy(() -> orderService.checkout(user, null, "PAC", null, address.getId()))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Endereço não encontrado");
        }
}
