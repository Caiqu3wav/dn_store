package com.dnstore.backend.service;

import com.dnstore.backend.dto.stock.StockDtos.SupplierRequest;
import com.dnstore.backend.dto.stock.StockDtos.SupplierResponse;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Supplier;
import com.dnstore.backend.repository.SupplierRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public Page<SupplierResponse> search(String q, Pageable pageable) {
        String term = q == null || q.isBlank() ? null : q.trim();
        return supplierRepository.search(term, pageable).map(SupplierService::toResponse);
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        String name = normalizeName(request.name());
        String document = normalizeDocument(request.document());
        if (supplierRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Já existe um fornecedor com este nome.");
        }
        if (document != null && supplierRepository.existsByDocument(document)) {
            throw new ConflictException("Já existe um fornecedor com este documento.");
        }
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setDocument(document);
        supplier.setActive(request.active() == null || request.active());
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado."));
        String name = normalizeName(request.name());
        String document = normalizeDocument(request.document());
        if (supplierRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Já existe um fornecedor com este nome.");
        }
        if (document != null && supplierRepository.existsByDocumentAndIdNot(document, id)) {
            throw new ConflictException("Já existe um fornecedor com este documento.");
        }
        supplier.setName(name);
        supplier.setDocument(document);
        if (request.active() != null) {
            supplier.setActive(request.active());
        }
        return toResponse(supplierRepository.save(supplier));
    }

    static String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Informe o nome do fornecedor.");
        }
        return normalized;
    }

    /** Mantém só dígitos; aceita CPF (11) ou CNPJ (14). Vazio vira null. */
    static String normalizeDocument(String document) {
        if (document == null || document.isBlank()) {
            return null;
        }
        String digits = document.replaceAll("\\D", "");
        if (digits.length() != 11 && digits.length() != 14) {
            throw new IllegalArgumentException("O documento deve ter 11 (CPF) ou 14 (CNPJ) dígitos.");
        }
        return digits;
    }

    private static SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getDocument(),
                supplier.isActive(), supplier.getCreatedAt());
    }
}
