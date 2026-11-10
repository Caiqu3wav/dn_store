package com.dnstore.backend.controller;

import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.dto.ProductResponse;
import com.dnstore.backend.dto.ProductDefaultRuleRequest;
import com.dnstore.backend.dto.ProductDefaultRuleResponse;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.service.ProductDefaultsService;
import com.dnstore.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * 🛒 ProductController - REST Completo
 *
 * API para gerenciamento de catálogo.
 * Suporta GET (Lista/Detalhe), POST (Criar), PUT (Web Atualizar), DELETE
 * (Remover).
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductDefaultsService productDefaultsService;

    /**
     * Lista todos os produtos.
     * GET /api/products
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> listAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sortBy) {
        return ResponseEntity.ok(
                productService.searchResponses(search, categoryId, minPrice, maxPrice, sortBy));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProductResponse>> listAllForAdmin(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String color) {
        return ResponseEntity.ok(
                productService.searchForAdminResponses(search, categoryId, minPrice, maxPrice, sortBy, color));
    }

    @GetMapping("/admin/default-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProductDefaultRuleResponse>> listDefaultRules() {
        return ResponseEntity.ok(productDefaultsService.listRules());
    }

    @PostMapping("/admin/default-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDefaultRuleResponse> createDefaultRule(
            @RequestBody ProductDefaultRuleRequest request) {
        return ResponseEntity.ok(productDefaultsService.createRule(request));
    }

    @DeleteMapping("/admin/default-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDefaultRule(@PathVariable UUID id) {
        productDefaultsService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Busca um produto por ID.
     * GET /api/products/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        return productService.findActiveResponseById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> getByIdForAdmin(@PathVariable UUID id) {
        return productService.findResponseById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
    }

    /**
     * Cria um novo produto físico.
     * POST /api/products
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> create(@RequestBody PhysicalProduct product) {
        Product created = productService.create(product);

        // Retorna 201 Created com Header Location
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /**
     * Cria vrios produtos em lote.
     * POST /api/products/batch
     */
    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Product>> createBatch(@RequestBody List<PhysicalProduct> products) {
        List<Product> created = productService.createAll(products);
        return ResponseEntity.ok(created);
    }

    /**
     * Atualiza um produto.
     * PUT /api/products/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> update(@PathVariable UUID id, @RequestBody PhysicalProduct product) {
        return productService.update(id, product)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
    }

    /**
     * Remove um produto.
     * DELETE /api/products/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (productService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        throw new ResourceNotFoundException("Produto não encontrado.");
    }
}
