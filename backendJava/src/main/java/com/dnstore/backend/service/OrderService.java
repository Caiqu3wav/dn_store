package com.dnstore.backend.service;

import com.dnstore.backend.model.Address;
import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.CartItem;
import com.dnstore.backend.model.Coupon;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderItem;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.ShippingQuoteOptionEntity;
import com.dnstore.backend.model.User;
import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.repository.AddressRepository;
import com.dnstore.backend.repository.CartRepository;
import com.dnstore.backend.repository.CouponRepository;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.ShippingQuoteOptionRepository;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.enums.PaymentStatus;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem;
import com.dnstore.backend.service.shipping.ShippingQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 📦 OrderService
 * 
 * Orquestra o fechamento do pedido.
 * Une os produtos do carrinho com o cálculo do frete e persiste no banco.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final DeliveryService deliveryService;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final CouponService couponService;
    private final CouponRepository couponRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderInventoryService orderInventoryService;
    private final PaymentRepository paymentRepository;
    private final ShippingQuoteOptionRepository shippingQuoteOptionRepository;
    private final MelhorEnvioProperties melhorEnvioProperties;

    @Value("${shipping.gateway:simulated}")
    private String shippingGateway;

    public record AddressData(
            String street, String number, String complement,
            String neighborhood, String city, String state, String zipCode) {
    }

    public record ShippingOption(
            UUID id,
            String serviceId,
            String typeName,
            BigDecimal cost,
            int deadLineDays,
            String source) {
    }

    @Transactional
    public List<ShippingOption> quoteShipping(User user, String zipCode) {
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ConflictException("O carrinho está vazio."));
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new ConflictException("O carrinho está vazio.");
        }

        List<ShippingItem> items = shippingItems(cart);
        String normalizedDestination = normalizeZip(zipCode);
        String fingerprint = cartFingerprint(items);
        String originZip = deliveryService.normalizedOriginZip();
        LocalDateTime quotedAt = now();
        LocalDateTime expiresAt = quotedAt.plus(melhorEnvioProperties.getQuoteTtl());

        return deliveryService.calculateOptions(normalizedDestination, items).stream()
                .map(option -> {
                    ShippingQuoteOptionEntity saved = new ShippingQuoteOptionEntity();
                    saved.setUser(user);
                    saved.setServiceId(option.serviceId());
                    saved.setServiceName(option.service());
                    saved.setPrice(option.price());
                    saved.setDeliveryDays(option.deliveryDays());
                    saved.setSource(option.source());
                    saved.setProviderEnvironment(providerEnvironment(option.source()));
                    saved.setOriginZip(originZip);
                    saved.setDestinationZip(normalizedDestination);
                    saved.setCartFingerprint(fingerprint);
                    saved.setQuotedAt(quotedAt);
                    saved.setExpiresAt(expiresAt);
                    saved = shippingQuoteOptionRepository.save(saved);
                    return new ShippingOption(
                            saved.getId(),
                            saved.getServiceId(),
                            saved.getServiceName(),
                            saved.getPrice(),
                            saved.getDeliveryDays(),
                            saved.getSource());
                })
                .toList();
    }

    @Transactional
    public Order checkout(
            User user,
            AddressData addressData,
            UUID shippingOptionId,
            String couponCode,
            UUID addressId) {
        if (user == null) {
            throw new IllegalArgumentException("Usuário autenticado é obrigatório.");
        }

        Cart cart = cartRepository.findByUserForUpdate(user)
                .orElseThrow(() -> new ConflictException("O carrinho está vazio."));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new ConflictException("O carrinho está vazio.");
        }

        Address address;
        String zipCode;

        if (addressId != null) {
            address = addressRepository.findByIdAndUser_Id(addressId, user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Endereço não encontrado."));
            zipCode = address.getZipCode();
        } else if (addressData != null) {
            if (addressData.street() == null || addressData.number() == null || addressData.city() == null
                    || addressData.state() == null || addressData.zipCode() == null
                    || addressData.zipCode().isBlank()) {
                throw new IllegalArgumentException("Dados de endereço incompletos.");
            }

            address = new Address();
            address.setUser(user);
            address.setStreet(addressData.street());
            address.setNumber(addressData.number());
            address.setComplement(addressData.complement());
            address.setNeighborhood(addressData.neighborhood());
            address.setCity(addressData.city());
            address.setState(addressData.state());
            address.setZipCode(addressData.zipCode());
            address = addressRepository.save(address);
            zipCode = addressData.zipCode();
        } else {
            throw new IllegalArgumentException("Endereço de entrega é obrigatório.");
        }

        if (zipCode == null || zipCode.isBlank()) {
            throw new IllegalArgumentException("CEP do endereço é obrigatório.");
        }

        List<UUID> variantIds = cart.getItems().stream()
                .map(cartItem -> cartItem.getProductVariant().getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .toList();

        java.util.Map<UUID, ProductVariant> lockedVariants = new java.util.LinkedHashMap<>();
        for (UUID variantId : variantIds) {
            ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
            lockedVariants.put(variantId, variant);
        }

        BigDecimal productTotal = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            if (cartItem == null || cartItem.getProductVariant() == null
                    || cartItem.getProductVariant().getProduct() == null) {
                throw new IllegalArgumentException("Produto do carrinho inválido.");
            }
            if (cartItem.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantidade inválida para o produto: "
                        + cartItem.getProductVariant().getProduct().getName());
            }

            ProductVariant variant = lockedVariants.get(cartItem.getProductVariant().getId());
            if (variant == null) {
                throw new ResourceNotFoundException("Produto não encontrado.");
            }
            if (!variant.isActive() || !variant.getProduct().isActive()) {
                throw new ConflictException("Uma variação do carrinho não está mais disponível.");
            }
            if (variant.getStock() < cartItem.getQuantity()) {
                log.warn("order.stock_insufficient variantId={} product='{}' available={} requested={}",
                        variant.getId(), variant.getProduct().getName(),
                        variant.getStock(), cartItem.getQuantity());
                throw new ConflictException(
                        "Estoque insuficiente para o produto: " + variant.getProduct().getName()
                                + " (" + variant.getSize() + ")");
            }

            BigDecimal unitPrice = cartItem.getProductVariant().getProduct().getPrice();
            productTotal = productTotal.add(unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        List<ShippingItem> currentShippingItems = shippingItems(cart, lockedVariants);
        ShippingQuoteOptionEntity selectedShipping = validateShippingSelection(
                user,
                shippingOptionId,
                zipCode,
                currentShippingItems);

        // 3. Aplicar Cupom se existir
        BigDecimal discount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            Coupon coupon = couponService.validateCoupon(couponCode)
                    .orElseThrow(() -> new IllegalArgumentException("Cupom inválido ou expirado."));

            if (coupon.getMinCartValue() != null && productTotal.compareTo(coupon.getMinCartValue()) < 0) {
                throw new IllegalArgumentException(
                        "O valor mínimo do carrinho para usar este cupom é R$ " + coupon.getMinCartValue());
            }

            if (coupon.getMaxUsage() != null && coupon.getCurrentUsage() != null
                    && coupon.getCurrentUsage() >= coupon.getMaxUsage()) {
                throw new IllegalArgumentException("Este cupom já atingiu o limite máximo de uso.");
            }

            if (coupon.getDiscountPercentage() != null) {
                discount = productTotal.multiply(coupon.getDiscountPercentage())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            } else if (coupon.getDiscountValue() != null) {
                discount = coupon.getDiscountValue();
            }

            if (discount.compareTo(productTotal) > 0) {
                discount = productTotal;
            }

            coupon.setCurrentUsage((coupon.getCurrentUsage() != null ? coupon.getCurrentUsage() : 0) + 1);
            couponRepository.save(coupon);
            appliedCoupon = coupon;
        }

        // 4. Criar Entidade Pedido
        Order order = new Order();
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("PENDING_PAYMENT");
        order.setUser(user);
        order.setAddress(address);
        order.setCoupon(appliedCoupon);
        order.setDiscountAmount(discount);

        // 4b. Validar estoque e decrementar
        for (CartItem cartItem : cart.getItems()) {
            ProductVariant variant = lockedVariants.get(cartItem.getProductVariant().getId());
            if (variant == null) {
                throw new ResourceNotFoundException("Produto não encontrado.");
            }
            variant.setStock(variant.getStock() - cartItem.getQuantity());
            productVariantRepository.save(variant);
        }

        List<OrderItem> orderItems = cart.getItems().stream()
                .map(cartItem -> new OrderItem(
                        order,
                        cartItem.getProductVariant(),
                        cartItem.getQuantity(),
                        cartItem.getProductVariant().getProduct().getPrice()))
                .collect(Collectors.toList());

        order.setItems(orderItems);
        order.setShippingCost(selectedShipping.getPrice());
        order.setShippingType(selectedShipping.getServiceName());
        order.setShippingServiceId(selectedShipping.getServiceId());
        order.setShippingSource(selectedShipping.getSource());
        order.setShippingQuoteOptionId(selectedShipping.getId());
        order.setShippingDeadlineDays(selectedShipping.getDeliveryDays());

        BigDecimal total = productTotal.add(selectedShipping.getPrice()).subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        order.setTotal(total);

        // 5. Persistir
        Order savedOrder = orderRepository.save(order);
        log.info("order.checkout_complete orderId={} userId={} total={} shipping={}",
                savedOrder.getId(), user.getId(), savedOrder.getTotal(), savedOrder.getShippingCost());

        // 6. Esvaziar carrinho
        cart.clear();
        cartRepository.save(cart);

        return savedOrder;
    }

    private List<ShippingItem> shippingItems(Cart cart) {
        return shippingItems(cart, null);
    }

    private List<ShippingItem> shippingItems(
            Cart cart,
            java.util.Map<UUID, ProductVariant> lockedVariants) {
        return cart.getItems().stream().map(item -> {
            ProductVariant variant = item == null || item.getProductVariant() == null
                    ? null
                    : lockedVariants == null
                            ? item.getProductVariant()
                            : lockedVariants.get(item.getProductVariant().getId());
            if (variant == null || !variant.isActive()
                    || !(variant.getProduct() instanceof PhysicalProduct product)) {
                throw new IllegalArgumentException("Produto físico inválido para cotação de frete.");
            }
            if (item.getQuantity() <= 0 || !isPositiveFinite(product.getWeight())
                    || !isPositiveFinite(product.getWidth()) || !isPositiveFinite(product.getHeight())
                    || !isPositiveFinite(product.getDepth())) {
                throw new IllegalArgumentException("Peso, dimensões e quantidade dos produtos devem ser válidos.");
            }
            return new ShippingItem(
                    variant.getId().toString(),
                    BigDecimal.valueOf(product.getWeight()),
                    BigDecimal.valueOf(product.getWidth()),
                    BigDecimal.valueOf(product.getHeight()),
                    BigDecimal.valueOf(product.getDepth()),
                    product.getPrice(),
                    item.getQuantity());
        }).toList();
    }

    private ShippingQuoteOptionEntity validateShippingSelection(
            User user,
            UUID shippingOptionId,
            String destinationZip,
            List<ShippingItem> items) {
        if (shippingOptionId == null) {
            throw new ConflictException("Selecione uma opção de frete cotada antes de finalizar o pedido.");
        }
        ShippingQuoteOptionEntity option = shippingQuoteOptionRepository
                .findByIdAndUser_IdAndExpiresAtAfter(shippingOptionId, user.getId(), now())
                .orElseThrow(() -> new ConflictException(
                        "A opção de frete expirou ou não pertence a este cliente. Consulte novamente."));

        if (!option.getDestinationZip().equals(normalizeZip(destinationZip))
                || !option.getOriginZip().equals(deliveryService.normalizedOriginZip())
                || !option.getCartFingerprint().equals(cartFingerprint(items))
                || !option.getProviderEnvironment().equals(providerEnvironment(option.getSource()))) {
            throw new ConflictException(
                    "Os dados do carrinho ou da cotação foram alterados. Consulte e confirme o frete novamente.");
        }
        return option;
    }

    private String cartFingerprint(List<ShippingItem> items) {
        String canonical = items.stream()
                .sorted(java.util.Comparator.comparing(ShippingItem::id))
                .map(item -> String.join("|",
                        item.id(),
                        item.weight().stripTrailingZeros().toPlainString(),
                        item.width().stripTrailingZeros().toPlainString(),
                        item.height().stripTrailingZeros().toPlainString(),
                        item.length().stripTrailingZeros().toPlainString(),
                        item.insuranceValue().stripTrailingZeros().toPlainString(),
                        Integer.toString(item.quantity())))
                .collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.");
        }
    }

    private String providerEnvironment(String source) {
        if ("MELHOR_ENVIO".equals(source) && "melhorEnvio".equals(shippingGateway)) {
            return melhorEnvioProperties.getEnvironment();
        }
        if ("SIMULATED".equals(source) && "simulated".equals(shippingGateway)) {
            return "simulated";
        }
        throw new ConflictException(
                "A configuração de frete mudou. Consulte e confirme uma nova opção antes de continuar.");
    }

    private String normalizeZip(String zipCode) {
        if (zipCode == null || !zipCode.trim().replaceAll("-", "").matches("\\d{8}")) {
            throw new IllegalArgumentException("CEP de entrega inválido.");
        }
        return zipCode.replace("-", "").trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static final java.util.Set<String> VALID_STATUSES = java.util.Set.of(
            "PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED", "PAYMENT_FAILED");

    public java.util.Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }

    /** Busca pedido garantindo que pertence ao usuário — previne IDOR. */
    public java.util.Optional<Order> findByIdAndUser(UUID id, UUID userId) {
        return orderRepository.findById(id)
                .filter(order -> order.getUser().getId().equals(userId));
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public List<Order> findByUser(UUID userId) {
        return orderRepository.findByUserId(userId);
    }

    @Transactional
    public java.util.Optional<Order> updateStatus(UUID id, String status) {
        if (!VALID_STATUSES.contains(status)) {
            log.warn("order.invalid_status status={}", status);
            throw new IllegalArgumentException("Status inválido: " + status);
        }
        java.util.Optional<Order> found = orderRepository.findByIdForUpdate(id);
        if (found.isEmpty()) {
            return java.util.Optional.empty();
        }
        Order order = found.get();
        if ("CANCELLED".equals(status)
                && ("PENDING_PAYMENT".equals(order.getStatus())
                        || "PAYMENT_FAILED".equals(order.getStatus()))) {
            List<Payment> attempts = paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(id);
            if (attempts.stream().anyMatch(payment ->
                    PaymentStatus.valueOf(payment.getStatus()) != PaymentStatus.FAILED)) {
                throw new ConflictException(
                        "Não é possível liberar o estoque enquanto houver uma tentativa de pagamento não reconciliada.");
            }
            orderInventoryService.releaseReservedStock(order);
        }
        log.info("order.status_changed orderId={} from={} to={}", 
            id, order.getStatus(), status);

        order.setStatus(status);

        return java.util.Optional.of(orderRepository.save(order));
    }
}
