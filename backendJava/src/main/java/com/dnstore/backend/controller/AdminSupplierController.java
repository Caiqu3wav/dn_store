package com.dnstore.backend.controller;

import com.dnstore.backend.dto.stock.StockDtos.PageResponse;
import com.dnstore.backend.dto.stock.StockDtos.SupplierRequest;
import com.dnstore.backend.dto.stock.StockDtos.SupplierResponse;
import com.dnstore.backend.service.SupplierService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fornecedores/fabricantes das notas de entrada. Somente administradores. */
@RestController
@RequestMapping("/api/admin/suppliers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public ResponseEntity<PageResponse<SupplierResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(PageResponse.of(supplierService.search(
                q, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))));
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        SupplierResponse created = supplierService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/suppliers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponse> update(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(supplierService.update(id, request));
    }
}
