package com.dnstore.backend.repository;

import com.dnstore.backend.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByUser_Id(UUID userId);

    Optional<Address> findByIdAndUser_Id(UUID addressId, UUID userId);
}
