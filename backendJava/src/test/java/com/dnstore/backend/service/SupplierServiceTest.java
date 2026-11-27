package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Supplier;
import com.dnstore.backend.repository.SupplierRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierServiceTest {

    private final SupplierRepository repository = mock(SupplierRepository.class);
    private final SupplierService service = new SupplierService(repository);

    @Test
    void createNormalizesTheDocumentToDigits() {
        when(repository.findByNameIgnoreCase("Fábrica X")).thenReturn(Optional.empty());
        when(repository.existsByDocument("12345678000199")).thenReturn(false);
        when(repository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        var created = service.create(" Fábrica X ", "12.345.678/0001-99", null);

        assertThat(created.name()).isEqualTo("Fábrica X");
        assertThat(created.document()).isEqualTo("12345678000199");
    }

    @Test
    void createRejectsDuplicatesAndInvalidInput() {
        when(repository.findByNameIgnoreCase("Fábrica X")).thenReturn(Optional.of(new Supplier()));

        assertThatThrownBy(() -> service.create("Fábrica X", null, null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.create("  ", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("Outra", "123", null)).isInstanceOf(IllegalArgumentException.class);

        when(repository.existsByDocument("12345678000199")).thenReturn(true);
        assertThatThrownBy(() -> service.create("Nova", "12345678000199", null)).isInstanceOf(ConflictException.class);
    }
}
