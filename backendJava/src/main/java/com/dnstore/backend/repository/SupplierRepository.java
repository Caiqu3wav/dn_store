package com.dnstore.backend.repository;

import com.dnstore.backend.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByNameIgnoreCase(String name);

    boolean existsByDocument(String document);

    @Query("SELECT s FROM Supplier s WHERE (:q = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR s.document LIKE CONCAT('%', :q, '%')) AND (:includeInactive = TRUE OR s.active = TRUE) " +
           "ORDER BY s.name")
    Page<Supplier> search(@Param("q") String q, @Param("includeInactive") boolean includeInactive,
                          Pageable pageable);
}
