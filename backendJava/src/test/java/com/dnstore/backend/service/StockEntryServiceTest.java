package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dnstore.backend.dto.stock.StockDtos.StockEntryItemRequest;
import com.dnstore.backend.dto.stock.StockDtos.StockEntryRequest;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.DuplicateStockEntryException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockEntry;
import com.dnstore.backend.model.StockEntryItem;
import com.dnstore.backend.model.Supplier;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.repository.StockEntryRepository;
import com.dnstore.backend.repository.SupplierRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class StockEntryServiceTest {

    @Mock
    private StockEntryRepository entryRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private StockLedgerService ledger;
    @Mock
    private PlatformTransactionManager transactionManager;

    private StockEntryService service;
    private User admin;
    private Supplier supplier;
    private ProductVariant first;
    private ProductVariant second;

    @BeforeEach
    void setUp() {
        service = new StockEntryService(entryRepository, supplierRepository, ledger, transactionManager);
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));
        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setRole(Role.ADMIN);
        supplier = new Supplier();
        supplier.setId(UUID.randomUUID());
        supplier.setName("Fabricante X");
        first = variant();
        second = variant();
    }

    @Test
    void create_whenActorIsNotAdmin_shouldBeDenied() {
        User customer = new User();
        customer.setRole(Role.USER);

        assertThatThrownBy(() -> service.create(request("NF-1", "k", line(first, 1)), customer))
                .isInstanceOf(AccessDeniedException.class);
        verify(entryRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_whenSameVariantAppearsTwice_shouldBeRejectedBeforeAnyTransaction() {
        assertThatThrownBy(() -> service.create(request("NF-1", "k", line(first, 1), line(first, 2)), admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mais de uma vez");
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void create_whenQuantityIsNotPositive_shouldBeRejected() {
        assertThatThrownBy(() -> service.create(request("NF-1", "k", line(first, 0)), admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(request("NF-1", "k", line(first, -3)), admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_whenInvoiceIsBlank_shouldBeRejected() {
        assertThatThrownBy(() -> service.create(request("   ", "k", line(first, 1)), admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_whenSupplierIsMissingOrInactive_shouldFailAndRollBack() {
        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request("NF-1", "k", line(first, 1)), admin))
                .isInstanceOf(ResourceNotFoundException.class);

        supplier.setActive(false);
        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        assertThatThrownBy(() -> service.create(request("NF-1", "k2", line(first, 1)), admin))
                .isInstanceOf(ConflictException.class).hasMessageContaining("inativo");
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void create_whenInvoiceAlreadyExistsForSupplier_shouldReportTheExistingEntry() {
        StockEntry existing = new StockEntry();
        existing.setId(UUID.randomUUID());
        when(entryRepository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        when(entryRepository.findBySupplier_IdAndInvoiceNumber(supplier.getId(), "NF-9"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(request(" nf-9 ", "new-key", line(first, 1)), admin))
                .isInstanceOfSatisfying(DuplicateStockEntryException.class,
                        e -> assertThat(e.getExistingEntryId()).isEqualTo(existing.getId()));
        verify(ledger, never()).applyDelta(any(), anyInt(), any());
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void create_withSeveralLines_shouldAppendEveryLineThroughTheLedgerInOneTransaction() {
        when(entryRepository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        when(entryRepository.findBySupplier_IdAndInvoiceNumber(any(), any())).thenReturn(Optional.empty());
        Map<UUID, ProductVariant> locked = new LinkedHashMap<>();
        locked.put(first.getId(), first);
        locked.put(second.getId(), second);
        when(ledger.lockVariants(any())).thenReturn(locked);
        when(entryRepository.saveAndFlush(any(StockEntry.class))).thenAnswer(invocation -> {
            StockEntry saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        StockEntryService.EntryResult result =
                service.create(request("NF-1", "k", line(first, 4), line(second, 6)), admin);

        assertThat(result.replayed()).isFalse();
        verify(ledger).applyDelta(any(ProductVariant.class), org.mockito.ArgumentMatchers.eq(4), any());
        verify(ledger).applyDelta(any(ProductVariant.class), org.mockito.ArgumentMatchers.eq(6), any());
        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void create_whenALaterLineFails_shouldRollBackTheWholeNote() {
        when(entryRepository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        when(entryRepository.findBySupplier_IdAndInvoiceNumber(any(), any())).thenReturn(Optional.empty());
        Map<UUID, ProductVariant> locked = new LinkedHashMap<>();
        locked.put(first.getId(), first);
        locked.put(second.getId(), second);
        when(ledger.lockVariants(any())).thenReturn(locked);
        when(entryRepository.saveAndFlush(any(StockEntry.class))).thenAnswer(i -> i.getArgument(0));
        when(ledger.applyDelta(any(ProductVariant.class), anyInt(), any()))
                .thenReturn(null)
                .thenThrow(new IllegalArgumentException("Quantidade fora do limite permitido."));

        assertThatThrownBy(() -> service.create(request("NF-1", "k", line(first, 4), line(second, 6)), admin))
                .isInstanceOf(IllegalArgumentException.class);

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void create_whenIdempotencyKeyWasUsedByTheSameNote_shouldReplayWithoutTouchingStock() {
        StockEntry stored = stored("NF-1", first, 4);
        when(entryRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(stored));

        StockEntryService.EntryResult result = service.create(request("nf-1", "k", line(first, 4)), admin);

        assertThat(result.replayed()).isTrue();
        assertThat(result.entryId()).isEqualTo(stored.getId());
        verify(ledger, never()).applyDelta(any(), anyInt(), any());
    }

    @Test
    void create_whenIdempotencyKeyBelongsToADifferentNote_shouldConflict() {
        StockEntry stored = stored("NF-1", first, 4);
        when(entryRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.create(request("NF-1", "k", line(first, 5)), admin))
                .isInstanceOf(ConflictException.class).hasMessageContaining("idempotência");
    }

    @Test
    void normalizeInvoice_shouldTrimCollapseSpacesAndUppercase() {
        assertThat(StockEntryService.normalizeInvoice("  nf   123-a ")).isEqualTo("NF 123-A");
    }

    // ---- helpers

    private StockEntry stored(String invoice, ProductVariant variant, int quantity) {
        StockEntry entry = new StockEntry();
        entry.setId(UUID.randomUUID());
        entry.setSupplier(supplier);
        entry.setInvoiceNumber(invoice);
        StockEntryItem item = new StockEntryItem();
        item.setProductVariant(variant);
        item.setQuantity(quantity);
        entry.addItem(item);
        return entry;
    }

    private StockEntryRequest request(String invoice, String key, StockEntryItemRequest... items) {
        return new StockEntryRequest(supplier.getId(), invoice, null, null, key, List.of(items));
    }

    private StockEntryItemRequest line(ProductVariant variant, int quantity) {
        return new StockEntryItemRequest(variant.getId(), quantity, BigDecimal.ONE);
    }

    private ProductVariant variant() {
        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setStock(2);
        return variant;
    }
}
