package com.dnstore.backend.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaModelConsistencyTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void orderItems_shouldUseOrderAsTheSingleOwningSideAndOrphanRemoval() throws Exception {
        Field itemsField = Order.class.getDeclaredField("items");
        OneToMany mapping = itemsField.getAnnotation(OneToMany.class);

        assertThat(mapping.mappedBy()).isEqualTo("order");
        assertThat(mapping.orphanRemoval()).isTrue();
        assertThat(OrderItem.class.getDeclaredField("order").getAnnotation(jakarta.persistence.ManyToOne.class))
                .isNotNull();
    }

    @Test
    void cartJson_shouldNotRecurseThroughCartItemBackReference() throws Exception {
        Cart cart = new Cart();
        CartItem item = new CartItem();
        item.setId(UUID.randomUUID());
        item.setCart(cart);
        item.setQuantity(2);
        cart.setItems(List.of(item));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(cart));

        assertThat(json.path("items")).hasSize(1);
        assertThat(json.path("items").get(0).has("cart")).isFalse();
    }

    @Test
    void orderJson_shouldNotRecurseThroughOrderItemBackReference() throws Exception {
        Order order = new Order();
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setQuantity(1);
        order.setItems(List.of(item));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(order));

        assertThat(json.path("items")).hasSize(1);
        assertThat(json.path("items").get(0).has("order")).isFalse();
    }

    @Test
    void userJson_shouldNotExposeCredentialsChallengesOrCpf() throws Exception {
        User user = new User();
        user.setPasswordHash("password-hash");
        user.setResetToken("reset-hash");
        user.setEmailVerificationCodeHash("email-code-hash");
        user.setMfaChallengeTokenHash("mfa-challenge-hash");
        user.setMfaCodeHash("mfa-code-hash");
        user.setCpf("12345678901");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(user));

        assertThat(json.has("passwordHash")).isFalse();
        assertThat(json.has("password")).isFalse();
        assertThat(json.has("resetToken")).isFalse();
        assertThat(json.has("emailVerificationCodeHash")).isFalse();
        assertThat(json.has("mfaChallengeTokenHash")).isFalse();
        assertThat(json.has("mfaCodeHash")).isFalse();
        assertThat(json.has("cpf")).isFalse();
    }

    @Test
    void entitiesShouldNotDeclareLombokStyleStructuralEquality() throws Exception {
        List<Class<?>> entities = List.of(
                Address.class, Cart.class, CartItem.class, Category.class, Coupon.class, Favorite.class,
                Order.class, OrderItem.class, Payment.class, PhysicalProduct.class, Product.class,
                ProductImage.class, ProductVariant.class, User.class);

        for (Class<?> entity : entities) {
            assertThat(entity.getDeclaredMethods())
                    .as("generated equality methods on %s", entity.getSimpleName())
                    .noneMatch(method -> method.getName().equals("equals")
                            || method.getName().equals("hashCode")
                            || method.getName().equals("toString"));
        }
    }

    @Test
    void entityBuilders_shouldPreservePersistedDefaults() {
        Order order = Order.builder().build();
        Coupon coupon = Coupon.builder().build();
        User user = User.builder().build();

        assertThat(order.getShippingCost()).isEqualByComparingTo("0.00");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(coupon.getCurrentUsage()).isZero();
        assertThat(coupon.getActive()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getRole()).isEqualTo(com.dnstore.backend.model.enums.Role.USER);
    }
}