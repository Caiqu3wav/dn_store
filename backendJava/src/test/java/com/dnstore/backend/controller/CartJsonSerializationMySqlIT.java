package com.dnstore.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.CartItem;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.CartRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regressão: GET /api/cart devolvia 500 quando o carrinho tinha itens, porque o Jackson não
 * serializa o proxy lazy do Hibernate (ByteBuddyInterceptor). Usa o mesmo MySQL local do
 * PaymentSchemaMySqlIT (127.0.0.1:33316) e não roda no `./mvnw test` padrão.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password"
        })
@ActiveProfiles("integration-local")
class CartJsonSerializationMySqlIT {

    private static final String EMAIL_PREFIX = "cart-json-it-";

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE ci FROM cart_items ci JOIN carts c ON c.id = ci.cart_id JOIN users u ON u.id = c.user_id "
                + "WHERE u.email LIKE ?", EMAIL_PREFIX + "%");
        jdbc.update("DELETE c FROM carts c JOIN users u ON u.id = c.user_id WHERE u.email LIKE ?", EMAIL_PREFIX + "%");
        jdbc.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%");
        jdbc.update("DELETE pv FROM product_variants pv JOIN products p ON p.id = pv.product_id "
                + "WHERE p.name = 'Cart JSON IT'");
        jdbc.update("DELETE pp FROM physical_products pp JOIN products p ON p.id = pp.id WHERE p.name = 'Cart JSON IT'");
        jdbc.update("DELETE FROM products WHERE name = 'Cart JSON IT'");
    }

    @Test
    void cartLoadedInANewSession_shouldSerializeLazyProductVariantProxies() {
        String email = EMAIL_PREFIX + UUID.randomUUID() + "@example.invalid";
        UUID[] userId = new UUID[1];
        transactions.executeWithoutResult(status -> {
            User user = new User();
            user.setName("Cart JSON");
            user.setEmail(email);
            user.setPasswordHash("x");
            entityManager.persist(user);
            userId[0] = user.getId();

            PhysicalProduct product = new PhysicalProduct();
            product.setName("Cart JSON IT");
            product.setPrice(new BigDecimal("10.00"));
            product.setWeight(0.3);
            product.setWidth(10);
            product.setHeight(10);
            product.setDepth(10);
            entityManager.persist(product);

            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setSize("M");
            variant.setStock(5);
            entityManager.persist(variant);

            Cart cart = new Cart();
            cart.setUser(user);
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProductVariant(variant);
            item.setQuantity(1);
            cart.getItems().add(item);
            entityManager.persist(cart);
        });

        String json = transactions.execute(status -> {
            entityManager.clear();
            // O User não está na sessão: Cart.user (lazy) e CartItem.productVariant (lazy) viram proxies,
            // como acontece em GET /api/cart com o usuário vindo do filtro JWT.
            Cart cart = cartRepository.findByUserId(userId[0]).orElseThrow();
            assertThat(cart.getUser()).isInstanceOf(org.hibernate.proxy.HibernateProxy.class);
            assertThat(cart.getItems().get(0).getProductVariant().isActive()).isTrue();
            try {
                return objectMapper.writeValueAsString(cart);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });

        assertThat(json).contains("\"size\":\"M\"").contains("\"quantity\":1");
    }
}
