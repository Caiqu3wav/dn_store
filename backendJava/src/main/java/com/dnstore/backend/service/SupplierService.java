package com.dnstore.backend.service;

import com.dnstore.backend.dto.StockDtos.PageView;
import com.dnstore.backend.dto.StockDtos.SupplierView;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Supplier;
import com.dnstore.backend.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierService {

    static final int MAX_PAGE_SIZE = 100;

    private final SupplierRepository supplierRepository;

    @Transactional
    public SupplierView create(String name, String document, String contact) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 150) {
            throw new IllegalArgumentException("Informe o nome do fornecedor (até 150 caracteres).");
        }
        String normalizedDocument = normalizeDocument(document);
        String normalizedContact = contact == null || contact.isBlank() ? null : contact.trim();
        if (normalizedContact != null && normalizedContact.length() > 255) {
            throw new IllegalArgumentException("O contato deve ter até 255 caracteres.");
        }
        if (supplierRepository.findByNameIgnoreCase(normalizedName).isPresent()) {
            throw new ConflictException("Já existe um fornecedor com esse nome.");
        }
        if (normalizedDocument != null && supplierRepository.existsByDocument(normalizedDocument)) {
            throw new ConflictException("Já existe um fornecedor com esse CNPJ.");
        }
        Supplier supplier = new Supplier();
        supplier.setName(normalizedName);
        supplier.setDocument(normalizedDocument);
        supplier.setContact(normalizedContact);
        return SupplierView.from(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    public PageView<SupplierView> search(String q, boolean includeInactive, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Page<Supplier> result = supplierRepository.search(q == null ? "" : q.trim(), includeInactive,
                PageRequest.of(safePage, safeSize));
        return PageView.of(result.getContent().stream().map(SupplierView::from).toList(), safePage, safeSize,
                result.getTotalElements());
    }

    private static String normalizeDocument(String document) {
        if (document == null || document.isBlank()) {
            return null;
        }
        String digits = document.replaceAll("\\D", "");
        if (digits.length() != 14) {
            throw new IllegalArgumentException("O CNPJ deve ter 14 dígitos.");
        }
        return digits;
    }
}
