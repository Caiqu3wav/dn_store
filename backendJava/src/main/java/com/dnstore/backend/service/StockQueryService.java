package com.dnstore.backend.service;

import com.dnstore.backend.dto.StockDtos.ChainBreak;
import com.dnstore.backend.dto.StockDtos.EntryDetail;
import com.dnstore.backend.dto.StockDtos.EntryItemView;
import com.dnstore.backend.dto.StockDtos.EntrySummary;
import com.dnstore.backend.dto.StockDtos.MovementView;
import com.dnstore.backend.dto.StockDtos.OrderStockView;
import com.dnstore.backend.dto.StockDtos.PageView;
import com.dnstore.backend.dto.StockDtos.ReconciliationDetail;
import com.dnstore.backend.dto.StockDtos.ReconciliationRow;
import com.dnstore.backend.dto.StockDtos.VariantView;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockEntry;
import com.dnstore.backend.model.StockEntryItem;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.repository.StockEntryItemRepository;
import com.dnstore.backend.repository.StockEntryRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Consultas do log, notas, pedidos que baixaram estoque e reconciliação. Somente leitura. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final EntityManager em;
    private final NamedParameterJdbcTemplate jdbc;
    private final StockMovementRepository movementRepository;
    private final StockEntryRepository entryRepository;
    private final StockEntryItemRepository itemRepository;

    public record MovementFilter(UUID productId, UUID variantId, Set<StockMovement.Type> types, LocalDate from,
                                 LocalDate to, String performedBy, UUID entryId, UUID orderId) {
        public static MovementFilter none() {
            return new MovementFilter(null, null, null, null, null, null, null, null);
        }
    }

    // ── Log ────────────────────────────────────────────────────────────────

    public PageView<MovementView> movements(MovementFilter f, int page, int size) {
        int safeSize = clampSize(size);
        int safePage = Math.max(page, 0);
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<StockMovement> countRoot = countQuery.from(StockMovement.class);
        countQuery.select(cb.count(countRoot)).where(movementPredicates(cb, countRoot, f));
        long total = em.createQuery(countQuery).getSingleResult();

        CriteriaQuery<StockMovement> query = cb.createQuery(StockMovement.class);
        Root<StockMovement> root = query.from(StockMovement.class);
        root.fetch("variant").fetch("product");
        var entryFetch = root.fetch("stockEntry", JoinType.LEFT);
        entryFetch.fetch("supplier", JoinType.LEFT);
        query.select(root).where(movementPredicates(cb, root, f)).orderBy(cb.desc(root.get("seq")));
        List<StockMovement> rows = em.createQuery(query)
                .setFirstResult(safePage * safeSize).setMaxResults(safeSize).getResultList();
        return PageView.of(rows.stream().map(MovementView::from).toList(), safePage, safeSize, total);
    }

    private Predicate[] movementPredicates(CriteriaBuilder cb, Root<StockMovement> m, MovementFilter f) {
        List<Predicate> p = new ArrayList<>();
        if (f.variantId() != null) {
            p.add(cb.equal(m.get("variant").get("id"), f.variantId()));
        }
        if (f.productId() != null) {
            p.add(cb.equal(m.get("variant").get("product").get("id"), f.productId()));
        }
        if (f.types() != null && !f.types().isEmpty()) {
            p.add(m.get("type").in(f.types()));
        }
        if (f.from() != null) {
            p.add(cb.greaterThanOrEqualTo(m.get("createdAt"), f.from().atStartOfDay()));
        }
        if (f.to() != null) {
            p.add(cb.lessThan(m.get("createdAt"), f.to().plusDays(1).atStartOfDay()));
        }
        if (f.performedBy() != null && !f.performedBy().isBlank()) {
            p.add(cb.like(cb.lower(m.get("performedBy")), "%" + escapeLike(f.performedBy().trim().toLowerCase()) + "%",
                    '\\'));
        }
        if (f.entryId() != null) {
            p.add(cb.equal(m.get("stockEntry").get("id"), f.entryId()));
        }
        if (f.orderId() != null) {
            p.add(cb.equal(m.get("order").get("id"), f.orderId()));
        }
        return p.toArray(Predicate[]::new);
    }

    // ── Variantes (busca para os formulários) ──────────────────────────────

    public PageView<VariantView> variants(String q, boolean includeInactive, UUID productId, int page, int size) {
        int safeSize = clampSize(size);
        int safePage = Math.max(page, 0);
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<ProductVariant> countRoot = countQuery.from(ProductVariant.class);
        countQuery.select(cb.count(countRoot)).where(variantPredicates(cb, countRoot, q, includeInactive, productId));
        long total = em.createQuery(countQuery).getSingleResult();

        CriteriaQuery<ProductVariant> query = cb.createQuery(ProductVariant.class);
        Root<ProductVariant> root = query.from(ProductVariant.class);
        root.fetch("product");
        query.select(root).where(variantPredicates(cb, root, q, includeInactive, productId))
                .orderBy(cb.asc(root.get("product").get("name")), cb.asc(root.get("size")));
        List<ProductVariant> rows = em.createQuery(query)
                .setFirstResult(safePage * safeSize).setMaxResults(safeSize).getResultList();
        return PageView.of(rows.stream().map(VariantView::from).toList(), safePage, safeSize, total);
    }

    private Predicate[] variantPredicates(CriteriaBuilder cb, Root<ProductVariant> v, String q,
                                          boolean includeInactive, UUID productId) {
        List<Predicate> p = new ArrayList<>();
        if (productId != null) {
            p.add(cb.equal(v.get("product").get("id"), productId));
        }
        if (!includeInactive) {
            p.add(cb.isTrue(v.get("active")));
            p.add(cb.isTrue(v.get("product").get("active")));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + escapeLike(q.trim().toLowerCase()) + "%";
            p.add(cb.or(
                    cb.like(cb.lower(v.get("product").get("name")), like, '\\'),
                    cb.like(cb.lower(v.get("sku")), like, '\\'),
                    cb.like(cb.lower(v.get("size")), like, '\\'),
                    cb.like(cb.lower(v.get("color")), like, '\\')));
        }
        return p.toArray(Predicate[]::new);
    }

    // ── Notas ──────────────────────────────────────────────────────────────

    public PageView<EntrySummary> entries(UUID supplierId, String q, LocalDate from, LocalDate to, int page,
                                          int size) {
        int safeSize = clampSize(size);
        int safePage = Math.max(page, 0);
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<StockEntry> countRoot = countQuery.from(StockEntry.class);
        countRoot.join("supplier");
        countQuery.select(cb.count(countRoot)).where(entryPredicates(cb, countRoot, supplierId, q, from, to));
        long total = em.createQuery(countQuery).getSingleResult();

        CriteriaQuery<StockEntry> query = cb.createQuery(StockEntry.class);
        Root<StockEntry> root = query.from(StockEntry.class);
        root.fetch("supplier");
        query.select(root).where(entryPredicates(cb, root, supplierId, q, from, to))
                .orderBy(cb.desc(root.get("createdAt")));
        TypedQuery<StockEntry> typed = em.createQuery(query);
        List<StockEntry> rows = typed.setFirstResult(safePage * safeSize).setMaxResults(safeSize).getResultList();
        Map<UUID, Totals> totals = totalsFor(rows.stream().map(StockEntry::getId).toList());
        return PageView.of(rows.stream().map(e -> summary(e, totals.getOrDefault(e.getId(), Totals.EMPTY))).toList(),
                safePage, safeSize, total);
    }

    private Predicate[] entryPredicates(CriteriaBuilder cb, Root<StockEntry> e, UUID supplierId, String q,
                                        LocalDate from, LocalDate to) {
        List<Predicate> p = new ArrayList<>();
        if (supplierId != null) {
            p.add(cb.equal(e.get("supplier").get("id"), supplierId));
        }
        if (from != null) {
            p.add(cb.greaterThanOrEqualTo(e.get("invoiceDate"), from));
        }
        if (to != null) {
            p.add(cb.lessThanOrEqualTo(e.get("invoiceDate"), to));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + escapeLike(q.trim().toLowerCase()) + "%";
            p.add(cb.or(
                    cb.like(cb.lower(e.get("invoiceNumber")), like, '\\'),
                    cb.like(cb.lower(e.get("supplier").get("name")), like, '\\')));
        }
        return p.toArray(Predicate[]::new);
    }

    public EntryDetail entryDetail(UUID id, boolean replayed) {
        StockEntry entry = entryRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nota de entrada não encontrada."));
        List<StockEntryItem> items = itemRepository.findByEntryId(id);
        List<MovementView> movements = movementRepository.findByEntryId(id).stream().map(MovementView::from).toList();
        return new EntryDetail(summary(entry, totalsFor(List.of(id)).getOrDefault(id, Totals.EMPTY)),
                entry.getNotes(), items.stream().map(EntryItemView::from).toList(), movements, replayed);
    }

    private record Totals(int items, int quantity, long costedItems, BigDecimal cost) {
        static final Totals EMPTY = new Totals(0, 0, 0, BigDecimal.ZERO);
    }

    private Map<UUID, Totals> totalsFor(Collection<UUID> entryIds) {
        Map<UUID, Totals> result = new HashMap<>();
        if (entryIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = em.createQuery(
                "SELECT i.entry.id, COUNT(i), SUM(i.quantity), COUNT(i.unitCost), SUM(i.quantity * i.unitCost) "
                        + "FROM StockEntryItem i WHERE i.entry.id IN :ids GROUP BY i.entry.id", Object[].class)
                .setParameter("ids", entryIds).getResultList();
        for (Object[] r : rows) {
            result.put((UUID) r[0], new Totals(((Number) r[1]).intValue(), ((Number) r[2]).intValue(),
                    ((Number) r[3]).longValue(), r[4] == null ? BigDecimal.ZERO : (BigDecimal) r[4]));
        }
        return result;
    }

    private EntrySummary summary(StockEntry e, Totals t) {
        // Custo total só é informado quando todas as linhas têm custo; senão seria um número enganoso.
        BigDecimal cost = t.items() > 0 && t.costedItems() == t.items() ? t.cost() : null;
        return new EntrySummary(e.getId(), e.getSupplier().getId(), e.getSupplier().getName(), e.getInvoiceNumber(),
                e.getInvoiceSeries(), e.getInvoiceDate(), t.items(), t.quantity(), cost, e.getCreatedBy(),
                e.getCreatedAt());
    }

    // ── Pedidos que baixaram estoque ───────────────────────────────────────

    public PageView<OrderStockView> orders(int page, int size) {
        int safeSize = clampSize(size);
        int safePage = Math.max(page, 0);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT order_id) FROM stock_movements WHERE order_id IS NOT NULL",
                new MapSqlParameterSource(), Long.class);
        List<OrderStockView> rows = jdbc.query("""
                SELECT o.id AS order_id, o.status, o.stock_released, o.total, o.created_at AS order_created_at,
                       COALESCE(SUM(CASE WHEN m.type IN ('SALE','REACTIVATION') THEN -m.delta ELSE 0 END), 0) AS reserved,
                       COALESCE(SUM(CASE WHEN m.type = 'CANCELLATION' THEN m.delta ELSE 0 END), 0) AS released,
                       COUNT(*) AS movement_count, MAX(m.created_at) AS last_movement_at
                FROM stock_movements m JOIN orders o ON o.id = m.order_id
                GROUP BY o.id, o.status, o.stock_released, o.total, o.created_at
                ORDER BY MAX(m.seq) DESC
                LIMIT :limit OFFSET :offset
                """,
                new MapSqlParameterSource().addValue("limit", safeSize).addValue("offset", safePage * safeSize),
                (rs, i) -> new OrderStockView(UUID.fromString(rs.getString("order_id")), rs.getString("status"),
                        rs.getBoolean("stock_released"), rs.getBigDecimal("total"),
                        rs.getTimestamp("order_created_at") == null ? null
                                : rs.getTimestamp("order_created_at").toLocalDateTime(),
                        rs.getInt("reserved"), rs.getInt("released"), rs.getInt("movement_count"),
                        rs.getTimestamp("last_movement_at").toLocalDateTime()));
        return PageView.of(rows, safePage, safeSize, total == null ? 0 : total);
    }

    // ── Reconciliação ──────────────────────────────────────────────────────

    /**
     * Compara o saldo da variante com o último movimento do livro. "Último" = maior created_at, desempate por seq:
     * o seq das linhas anteriores à V30 foi preenchido na ordem física da tabela e não é cronológico entre elas.
     */
    public PageView<ReconciliationRow> reconciliation(UUID productId, boolean onlyIssues, int page, int size) {
        int safeSize = clampSize(size);
        int safePage = Math.max(page, 0);
        String from = """
                FROM product_variants v
                JOIN products p ON p.id = v.product_id
                LEFT JOIN stock_movements lm ON lm.seq = (
                    SELECT m2.seq FROM stock_movements m2 WHERE m2.variant_id = v.id
                    ORDER BY m2.created_at DESC, m2.seq DESC LIMIT 1)
                WHERE (:productId IS NULL OR v.product_id = :productId)
                  AND (:onlyIssues = FALSE OR lm.seq IS NULL OR lm.stock_after <> v.stock)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("productId", productId == null ? null : productId.toString())
                .addValue("onlyIssues", onlyIssues)
                .addValue("limit", safeSize).addValue("offset", safePage * safeSize);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from, params, Long.class);
        List<ReconciliationRow> rows = jdbc.query("""
                SELECT v.id AS variant_id, p.id AS product_id, p.name AS product_name, v.size, v.color, v.active,
                       v.stock, lm.stock_after AS ledger_balance, lm.seq AS last_seq
                """ + from + " ORDER BY p.name, v.size LIMIT :limit OFFSET :offset", params, (rs, i) -> {
            int stock = rs.getInt("stock");
            Integer ledger = rs.getObject("last_seq") == null ? null : rs.getInt("ledger_balance");
            return new ReconciliationRow(UUID.fromString(rs.getString("variant_id")),
                    UUID.fromString(rs.getString("product_id")), rs.getString("product_name"), rs.getString("size"),
                    rs.getString("color"), rs.getBoolean("active"), stock, ledger,
                    ledger == null ? null : stock - ledger,
                    ledger == null ? "NO_LEDGER" : ledger == stock ? "OK" : "DIVERGENT");
        });
        return PageView.of(rows, safePage, safeSize, total == null ? 0 : total);
    }

    public ReconciliationDetail reconciliationOf(UUID variantId) {
        ReconciliationRow row = singleRow(variantId);
        // Quebra de cadeia: stock_before diferente do stock_after da linha anterior (por seq). Só compara duas
        // linhas novas (stock_before preenchido nas duas); linhas legadas não têm saldo anterior.
        List<ChainBreak> breaks = jdbc.query("""
                SELECT seq, stock_before, prev_after FROM (
                    SELECT seq, stock_before, LAG(stock_after) OVER w AS prev_after, LAG(stock_before) OVER w AS prev_before
                    FROM stock_movements WHERE variant_id = :variantId
                    WINDOW w AS (ORDER BY seq)) t
                WHERE stock_before IS NOT NULL AND prev_before IS NOT NULL AND stock_before <> prev_after
                ORDER BY seq
                """, new MapSqlParameterSource("variantId", variantId.toString()),
                (rs, i) -> new ChainBreak(rs.getLong("seq"), rs.getInt("stock_before"), rs.getInt("prev_after")));
        return new ReconciliationDetail(row, breaks);
    }

    private ReconciliationRow singleRow(UUID variantId) {
        List<ReconciliationRow> rows = jdbc.query("""
                SELECT v.id AS variant_id, p.id AS product_id, p.name AS product_name, v.size, v.color, v.active,
                       v.stock, lm.stock_after AS ledger_balance, lm.seq AS last_seq
                FROM product_variants v JOIN products p ON p.id = v.product_id
                LEFT JOIN stock_movements lm ON lm.seq = (
                    SELECT m2.seq FROM stock_movements m2 WHERE m2.variant_id = v.id
                    ORDER BY m2.created_at DESC, m2.seq DESC LIMIT 1)
                WHERE v.id = :id
                """, new MapSqlParameterSource("id", variantId.toString()), (rs, i) -> {
            int stock = rs.getInt("stock");
            Integer ledger = rs.getObject("last_seq") == null ? null : rs.getInt("ledger_balance");
            return new ReconciliationRow(UUID.fromString(rs.getString("variant_id")),
                    UUID.fromString(rs.getString("product_id")), rs.getString("product_name"), rs.getString("size"),
                    rs.getString("color"), rs.getBoolean("active"), stock, ledger,
                    ledger == null ? null : stock - ledger,
                    ledger == null ? "NO_LEDGER" : ledger == stock ? "OK" : "DIVERGENT");
        });
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Variação não encontrada.");
        }
        return rows.get(0);
    }

    // ── util ───────────────────────────────────────────────────────────────

    static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
