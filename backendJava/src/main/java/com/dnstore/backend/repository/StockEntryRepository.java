package com.dnstore.backend.repository;

import com.dnstore.backend.model.StockEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockEntryRepository extends JpaRepository<StockEntry, UUID>, JpaSpecificationExecutor<StockEntry> {

    Optional<StockEntry> findByIdempotencyKey(String idempotencyKey);

    Optional<StockEntry> findBySupplier_IdAndInvoiceNumber(UUID supplierId, String invoiceNumber);

    @EntityGraph(attributePaths = { "supplier", "createdBy" })
    @Query("SELECT e FROM StockEntry e WHERE e.id = :id")
    Optional<StockEntry> findDetailedById(@Param("id") UUID id);
}
