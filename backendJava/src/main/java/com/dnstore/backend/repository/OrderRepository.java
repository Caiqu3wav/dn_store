package com.dnstore.backend.repository;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUser(User user);

    List<Order> findByUserId(UUID userId);

    List<Order> findByStatus(String status);
}
