package com.dnstore.backend.repository;

import com.dnstore.backend.model.Supplier;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByDocument(String document);

    boolean existsByDocumentAndIdNot(String document, UUID id);

    @Query("""
            SELECT s FROM Supplier s
            WHERE (:q IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) OR s.document LIKE CONCAT('%', :q, '%'))
            ORDER BY s.name
            """)
    Page<Supplier> search(@Param("q") String q, Pageable pageable);
}
