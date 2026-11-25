package com.dnstore.backend.service;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Address;
import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.CartItem;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.ShippingQuoteOptionEntity;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.repository.AddressRepository;
import com.dnstore.backend.repository.CartRepository;
import com.dnstore.backend.repository.CouponRepository;
import com.dnstore.backend.repository.FinancialBlockRepository;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.ShippingQuoteOptionRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Testes realistas de fluxo ponta a ponta de estoque, compra, cancelamento,
 * expiração, reativação e baixa manual com serviços reais e estado persistido em memória.
 */
@ExtendWith(MockitoExtension.class)
class StockPurchaseFlowTest {

    @Mock private DeliveryService deliveryService;
    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private CouponService couponService;
    @Mock private CouponRepository couponRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private FinancialBlockRepository financialBlockRepository;
    @Mock private ShippingQuoteOptionRepository shippingQuoteOptionRepository;
    @Mock private StockMovementRepository movementRepository;
    @Mock private PaymentGateway paymentGateway;

    private StockService stockService;
    private OrderInventoryService orderInventoryService;
    private OrderService orderService;
    private PaymentExpirationService expirationService;

    private final Map<UUID, ProductVariant> variantStore = new LinkedHashMap<>();
    private final Map<UUID, Order> orderStore = new LinkedHashMap<>();
    private final List<StockMovement> movementStore = new ArrayList<>();

    private User customer;
    private Address address;
    private ProductVariant variantM;
    private ShippingQuoteOptionEntity shippingOption;

    @BeforeEach
    void setUp() {
        variantStore.clear();
        orderStore.clear();
        movementStore.clear();

        MelhorEnvioProperties melhorEnvioProperties = new MelhorEnvioProperties();
        melhorEnvioProperties.setQuoteTtl(Duration.ofMinutes(15));
        melhorEnvioProperties.setQuoteReuseWindow(Duration.ofMinutes(5));

        stockService = new StockService(variantRepository, movementRepository);
        orderInventoryService = new OrderInventoryService(variantRepository, couponService);
        ReflectionTestUtils.setField(orderInventoryService, "stockService", stockService);

        orderService = new OrderService(
                deliveryService,
                orderRepository,
                cartRepository,
                addressRepository,
                couponService,
                couponRepository,
                variantRepository,
                orderInventoryService,
                paymentRepository,
                financialBlockRepository,
                shippingQuoteOptionRepository,
                melhorEnvioProperties);
        ReflectionTestUtils.setField(orderService, "stockService", stockService);
        ReflectionTestUtils.setField(orderService, "shippingGateway", "simulated");

        expirationService = new PaymentExpirationService(
                orderRepository, paymentRepository, orderInventoryService, paymentGateway);
        ReflectionTestUtils.setField(expirationService, "orderExpirationMinutes", 60L);

        // Stateful variant repository
        lenient().when(variantRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(variantStore.get(id));
        });
        lenient().when(variantRepository.findAllByIdInForUpdate(anyCollection())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            List<ProductVariant> result = new ArrayList<>();
            for (UUID id : ids) {
                ProductVariant v = variantStore.get(id);
                if (v != null) result.add(v);
            }
            return result;
        });
        lenient().when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> {
            ProductVariant v = inv.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            variantStore.put(v.getId(), v);
            return v;
        });

        // Stateful stock movement repository
        lenient().when(movementRepository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            if (m.getCreatedAt() == null) m.setCreatedAt(LocalDateTime.now());
            movementStore.add(m);
            return m;
        });
        lenient().when(movementRepository.findByVariantIdOrderByCreatedAtDesc(any(UUID.class))).thenAnswer(inv -> {
            UUID vId = inv.getArgument(0);
            return movementStore.stream()
                    .filter(m -> m.getVariant().getId().equals(vId))
                    .toList();
        });

        // Stateful order repository
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(UUID.randomUUID());
            orderStore.put(o.getId(), o);
            return o;
        });
        lenient().when(orderRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(orderStore.get(id));
        });

        // Fixtures
        customer = new User();
        customer.setId(UUID.randomUUID());
        customer.setName("Cliente Teste");
        customer.setEmail("cliente@dnstore.test");
        customer.setRole(Role.USER);

        address = new Address();
        address.setId(UUID.randomUUID());
        address.setUser(customer);
        address.setStreet("Rua das Flores");
        address.setNumber("100");
        address.setNeighborhood("Centro");
        address.setCity("São Paulo");
        address.setState("SP");
        address.setZipCode("01001-000");

        PhysicalProduct product = new PhysicalProduct();
        product.setId(UUID.randomUUID());
        product.setName("Camiseta DN Oversized");
        product.setPrice(new BigDecimal("149.90"));
        product.setActive(true);
        product.setWeight(0.4);
        product.setWidth(20);
        product.setHeight(5);
        product.setDepth(30);

        variantM = new ProductVariant();
        variantM.setId(UUID.randomUUID());
        variantM.setProduct(product);
        variantM.setSize("M");
        variantM.setColor("Preto");
        variantM.setStock(10);
        variantM.setActive(true);
        variantStore.put(variantM.getId(), variantM);

        shippingOption = new ShippingQuoteOptionEntity();
        shippingOption.setId(UUID.randomUUID());
        shippingOption.setUser(customer);
        shippingOption.setServiceId("1");
        shippingOption.setServiceName("PAC");
        shippingOption.setPrice(new BigDecimal("20.00"));
        shippingOption.setDeliveryDays(5);
        shippingOption.setSource("SIMULATED");
        shippingOption.setProviderEnvironment("simulated");
        shippingOption.setOriginZip("01000000");
        shippingOption.setDestinationZip("01001000");
        shippingOption.setExpiresAt(LocalDateTime.now().plusMinutes(15));

        lenient().when(addressRepository.findByIdAndUser_Id(address.getId(), customer.getId()))
                .thenReturn(Optional.of(address));
        lenient().when(deliveryService.normalizedOriginZip()).thenReturn("01000000");
        lenient().when(paymentRepository.findByOrderIdForUpdate(any(UUID.class))).thenReturn(List.of());
        lenient().when(paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(any(UUID.class))).thenReturn(List.of());
        lenient().when(financialBlockRepository.existsByOrder_IdAndActiveTrue(any(UUID.class))).thenReturn(false);
        lenient().when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Cart prepareCartWithQuantity(int qty) {
        Cart cart = new Cart();
        cart.setUser(customer);
        cart.setItems(new ArrayList<>());
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProductVariant(variantM);
        item.setQuantity(qty);
        cart.getItems().add(item);

        String fingerprint = String.join("|",
                variantM.getId().toString(),
                "0.4", "20", "5", "30", "149.9",
                Integer.toString(qty));
        lenient().when(cartRepository.findByUserForUpdate(customer)).thenReturn(Optional.of(cart));
        lenient().when(shippingQuoteOptionRepository.findByIdAndUser_IdAndExpiresAtAfter(
                eq(shippingOption.getId()), eq(customer.getId()), any(LocalDateTime.class)))
                .thenReturn(Optional.of(shippingOption));
        // Stub fingerprint via reflection-free approach: calculate same SHA-256 or use lenient?
        // Actually OrderService checks shippingOption.getCartFingerprint().equals(cartFingerprint(items)):
        // Let's set the exact SHA-256 fingerprint on shippingOption!
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            shippingOption.setCartFingerprint(java.util.HexFormat.of().formatHex(hash));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return cart;
    }

    @Test
    void fullPurchaseAndCancellationFlow_updatesStockAndRecordsMovements() {
        // 1. Entrada de +5 unidades (10 -> 15)
        stockService.entry(
                List.of(new StockService.StockEntryItem(variantM.getId(), 5, "NF 1020")),
                "admin@dnstore.test");
        assertThat(variantM.getStock()).isEqualTo(15);

        // 2. Compra de 4 unidades (15 -> 11)
        Cart cart = prepareCartWithQuantity(4);
        Order order = orderService.checkout(customer, null, shippingOption.getId(), null, address.getId());

        assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.getTotal()).isEqualByComparingTo("619.60"); // 4 * 149.90 + 20.00
        assertThat(variantM.getStock()).isEqualTo(11);
        assertThat(cart.getItems()).isEmpty();

        // 3. Cancelamento do pedido (11 -> 15)
        orderService.updateStatus(order.getId(), "CANCELLED");
        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(order.isStockReleased()).isTrue();
        assertThat(variantM.getStock()).isEqualTo(15);

        // 4. Histórico completo: ENTRY (+5, after 15), SALE (-4, after 11), CANCELLATION (+4, after 15)
        assertThat(movementStore).hasSize(3);
        assertThat(movementStore.get(0).getType()).isEqualTo(StockMovement.Type.ENTRY);
        assertThat(movementStore.get(0).getDelta()).isEqualTo(5);
        assertThat(movementStore.get(0).getStockAfter()).isEqualTo(15);

        assertThat(movementStore.get(1).getType()).isEqualTo(StockMovement.Type.SALE);
        assertThat(movementStore.get(1).getDelta()).isEqualTo(-4);
        assertThat(movementStore.get(1).getStockAfter()).isEqualTo(11);
        assertThat(movementStore.get(1).getOrder()).isEqualTo(order);

        assertThat(movementStore.get(2).getType()).isEqualTo(StockMovement.Type.CANCELLATION);
        assertThat(movementStore.get(2).getDelta()).isEqualTo(4);
        assertThat(movementStore.get(2).getStockAfter()).isEqualTo(15);
        assertThat(movementStore.get(2).getOrder()).isEqualTo(order);
    }

    @Test
    void checkoutAboveAvailableStock_throwsConflictWithoutModifyingStockOrMovements() {
        prepareCartWithQuantity(11); // só há 10 em estoque

        assertThatThrownBy(() -> orderService.checkout(
                customer, null, shippingOption.getId(), null, address.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Estoque insuficiente");

        assertThat(variantM.getStock()).isEqualTo(10);
        assertThat(movementStore).isEmpty();
        assertThat(orderStore).isEmpty();
    }

    @Test
    void expirationAndReactivationFlow_releasesAndReservesStockWithMovements() {
        prepareCartWithQuantity(3);
        Order order = orderService.checkout(customer, null, shippingOption.getId(), null, address.getId());
        assertThat(variantM.getStock()).isEqualTo(7);

        // Simula pedido expirado (criado há 90 min)
        order.setCreatedAt(LocalDateTime.now().minusMinutes(90));
        expirationService.expireIfDue(order.getId());

        assertThat(order.getStatus()).isEqualTo("EXPIRED");
        assertThat(order.isStockReleased()).isTrue();
        assertThat(variantM.getStock()).isEqualTo(10);

        // Reativação de pedido (ex.: pagamento aprovado tardiamente via revisão)
        orderInventoryService.reserveReleasedStock(order);
        assertThat(order.isStockReleased()).isFalse();
        assertThat(variantM.getStock()).isEqualTo(7);

        assertThat(movementStore)
                .extracting(StockMovement::getType)
                .containsExactly(
                        StockMovement.Type.SALE,
                        StockMovement.Type.CANCELLATION,
                        StockMovement.Type.SALE);
        assertThat(movementStore.get(2).getStockAfter()).isEqualTo(7);
    }

    @Test
    void manualWriteOff_decrementsStockAndBlocksNegativeResult() {
        StockMovement writeOff = stockService.adjust(
                variantM.getId(), -2, "Avaria na estampa", "admin@dnstore.test");

        assertThat(variantM.getStock()).isEqualTo(8);
        assertThat(writeOff.getType()).isEqualTo(StockMovement.Type.ADJUSTMENT);
        assertThat(writeOff.getDelta()).isEqualTo(-2);
        assertThat(writeOff.getStockAfter()).isEqualTo(8);

        assertThatThrownBy(() -> stockService.adjust(
                variantM.getId(), -9, "Baixa excessiva", "admin@dnstore.test"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("estoque negativo");

        assertThat(variantM.getStock()).isEqualTo(8);
    }
}
