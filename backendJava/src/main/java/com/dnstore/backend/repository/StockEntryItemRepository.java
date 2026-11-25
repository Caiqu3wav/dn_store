package com.dnstore.backend.repository;

import com.dnstore.backend.model.StockEntryItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockEntryItemRepository extends JpaRepository<StockEntryItem, UUID> {

    @Query("""
            SELECT i.entry.id AS entryId, COUNT(i) AS itemCount, SUM(i.quantity) AS totalUnits
            FROM StockEntryItem i WHERE i.entry.id IN :entryIds GROUP BY i.entry.id
            """)
    List<EntryTotals> totalsByEntry(@Param("entryIds") Collection<UUID> entryIds);

    interface EntryTotals {
        UUID getEntryId();

        Long getItemCount();

        Long getTotalUnits();
    }
}
