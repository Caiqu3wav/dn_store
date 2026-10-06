package com.dnstore.backend.controller;

import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<User>> listAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<User> updateRole(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String roleStr = body.get("role");
        if (roleStr == null) {
            throw new IllegalArgumentException("Role é obrigatória.");
        }
        Role newRole = Role.valueOf(roleStr.toUpperCase(java.util.Locale.ROOT));
        return userService.updateRole(id, newRole)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new com.dnstore.backend.exception.ResourceNotFoundException(
                        "Usuário não encontrado."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (userService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        throw new com.dnstore.backend.exception.ResourceNotFoundException("Usuário não encontrado.");
    }
}
