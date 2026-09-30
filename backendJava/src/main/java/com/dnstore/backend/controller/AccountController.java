package com.dnstore.backend.controller;

import com.dnstore.backend.model.Address;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.User;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.repository.AddressRepository;
import com.dnstore.backend.repository.UserRepository;
import com.dnstore.backend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

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
    public ResponseEntity<AccountProfileResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new AccountProfileResponse(user));
    }

    @PutMapping("/me")
    public ResponseEntity<User> updateMe(@AuthenticationPrincipal User user, @RequestBody Map<String, String> body) {
        if (body.containsKey("name") && !body.get("name").isBlank())
            user.setName(body.get("name"));
        if (body.containsKey("phone"))
            user.setPhone(body.get("phone"));
        return ResponseEntity.ok(userRepository.save(user));
    }

    @PutMapping("/me/email-mfa")
    public ResponseEntity<AccountProfileResponse> updateEmailMfa(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null)
            throw new IllegalArgumentException("O estado do MFA é obrigatório.");
        user.setEmailMfaEnabled(enabled);
        if (!enabled) {
            user.setMfaChallengeTokenHash(null);
            user.setMfaCodeHash(null);
            user.setMfaCodeExpiration(null);
            user.setMfaAttempts(0);
        }
        return ResponseEntity.ok(new AccountProfileResponse(userRepository.save(user)));
    }

    @GetMapping("/addresses")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AddressResponse>> addresses(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(addressRepository.findByUser_Id(user.getId()).stream()
                .map(AddressResponse::new)
                .toList());
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> addAddress(@AuthenticationPrincipal User user,
            @RequestBody Address address) {
        address.setId(null);
        address.setUser(user);
        return ResponseEntity.status(201).body(new AddressResponse(addressRepository.save(address)));
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        Address address = addressRepository.findById(id)
                .filter(candidate -> candidate.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Endereço não encontrado."));
        addressRepository.delete(address);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> orders(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.findByUser(user.getId()));
    }

    public static class AddressResponse {
        private final UUID id;
        private final String street;
        private final String number;
        private final String complement;
        private final String neighborhood;
        private final String city;
        private final String state;
        private final String zipCode;

        public AddressResponse(Address address) {
            this.id = address.getId();
            this.street = address.getStreet();
            this.number = address.getNumber();
            this.complement = address.getComplement();
            this.neighborhood = address.getNeighborhood();
            this.city = address.getCity();
            this.state = address.getState();
            this.zipCode = address.getZipCode();
        }

        public UUID getId() {
            return id;
        }

        public String getStreet() {
            return street;
        }

        public String getNumber() {
            return number;
        }

        public String getComplement() {
            return complement;
        }

        public String getNeighborhood() {
            return neighborhood;
        }

        public String getCity() {
            return city;
        }

        public String getState() {
            return state;
        }

        public String getZipCode() {
            return zipCode;
        }
    }

    public record AccountProfileResponse(
            UUID id, String name, String email, String phone, Role role, boolean emailMfaEnabled) {
        public AccountProfileResponse(User user) {
            this(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole(),
                    user.isEmailMfaEnabled());
        }
    }
}
