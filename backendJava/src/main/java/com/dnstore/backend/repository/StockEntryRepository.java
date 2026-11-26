package com.dnstore.backend.repository;

import com.dnstore.backend.model.StockEntry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Notas são imutáveis: sem delete. */
public interface StockEntryRepository extends Repository<StockEntry, UUID> {

    StockEntry save(StockEntry entry);

    @Query("SELECT e FROM StockEntry e JOIN FETCH e.supplier WHERE e.id = :id")
    Optional<StockEntry> findDetailedById(@Param("id") UUID id);

    @Query("SELECT e FROM StockEntry e JOIN FETCH e.supplier WHERE e.idempotencyKey = :key")
    Optional<StockEntry> findByIdempotencyKey(@Param("key") String key);

    @Query("SELECT e FROM StockEntry e JOIN FETCH e.supplier WHERE e.supplier.id = :supplierId " +
           "AND e.invoiceSeries = :series AND e.invoiceNumber = :number")
    Optional<StockEntry> findByInvoice(@Param("supplierId") UUID supplierId,
                                       @Param("series") String series,
                                       @Param("number") String number);
}
