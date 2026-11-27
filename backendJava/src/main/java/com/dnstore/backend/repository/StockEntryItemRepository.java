package com.dnstore.backend.repository;

import com.dnstore.backend.model.StockEntryItem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockEntryItemRepository extends Repository<StockEntryItem, UUID> {

    StockEntryItem save(StockEntryItem item);

    @Query("SELECT i FROM StockEntryItem i JOIN FETCH i.variant v JOIN FETCH v.product " +
           "WHERE i.entry.id = :entryId ORDER BY v.product.name, v.size")
    List<StockEntryItem> findByEntryId(@Param("entryId") UUID entryId);
}
