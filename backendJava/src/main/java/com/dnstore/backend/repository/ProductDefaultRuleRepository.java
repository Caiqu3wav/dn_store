package com.dnstore.backend.repository;

import com.dnstore.backend.model.ProductDefaultRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductDefaultRuleRepository extends JpaRepository<ProductDefaultRule, UUID> {

    List<ProductDefaultRule> findAllByOrderByCreatedAtAscIdAsc();

    List<ProductDefaultRule> findAllByOrderByCreatedAtDescIdDesc();
}
