package com.dnstore.backend.controller;

import com.dnstore.backend.model.Address;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.AddressRepository;
import com.dnstore.backend.repository.UserRepository;
import com.dnstore.backend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final OrderService orderService;

    @GetMapping("/me")
    public ResponseEntity<User> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    public ResponseEntity<User> updateMe(@AuthenticationPrincipal User user, @RequestBody Map<String, String> body) {
        if (body.containsKey("name") && !body.get("name").isBlank()) user.setName(body.get("name"));
        if (body.containsKey("phone")) user.setPhone(body.get("phone"));
        return ResponseEntity.ok(userRepository.save(user));
    }

    @GetMapping("/addresses")
    public ResponseEntity<List<Address>> addresses(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(addressRepository.findByUser_Id(user.getId()));
    }

    @PostMapping("/addresses")
    public ResponseEntity<Address> addAddress(@AuthenticationPrincipal User user, @RequestBody Address address) {
        address.setId(null);
        address.setUser(user);
        return ResponseEntity.status(201).body(addressRepository.save(address));
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return addressRepository.findById(id)
                .filter(address -> address.getUser().getId().equals(user.getId()))
                .map(address -> { addressRepository.delete(address); return ResponseEntity.noContent().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> orders(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.findByUser(user.getId()));
    }
}
