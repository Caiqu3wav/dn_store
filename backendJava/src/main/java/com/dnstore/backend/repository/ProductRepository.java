package com.dnstore.backend.repository;

import com.dnstore.backend.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    @Override
    @EntityGraph(attributePaths = "variants")
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = "variants")
    Optional<Product> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = "variants")
    List<Product> findAll(Specification<Product> spec, Sort sort);

    @EntityGraph(attributePaths = "variants")
    List<Product> findByActiveTrue();

    @EntityGraph(attributePaths = "variants")
    List<Product> findByNameContainingIgnoreCase(String name);

    @EntityGraph(attributePaths = "variants")
    @Query("""
                SELECT p FROM Product p
                WHERE p.active = true
                AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
                AND (:minPrice IS NULL OR p.price >= :minPrice)
                AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            """)
    List<Product> searchProducts(
            @Param("search") String search,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice);
}
