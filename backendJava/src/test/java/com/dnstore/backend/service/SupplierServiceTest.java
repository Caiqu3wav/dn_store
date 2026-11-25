package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dnstore.backend.dto.stock.StockDtos.SupplierRequest;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Supplier;
import com.dnstore.backend.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;
    @InjectMocks
    private SupplierService service;

    @Test
    void create_shouldKeepOnlyDocumentDigitsAndCollapseNameSpaces() {
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new SupplierRequest("  Fabricante   X ", "12.345.678/0001-95", null));

        assertThat(response.name()).isEqualTo("Fabricante X");
        assertThat(response.document()).isEqualTo("12345678000195");
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_whenDocumentHasWrongLength_shouldBeRejected() {
        assertThatThrownBy(() -> service.create(new SupplierRequest("Fabricante", "123", null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void create_whenNameAlreadyExists_shouldConflict() {
        when(supplierRepository.existsByNameIgnoreCase("Fabricante")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SupplierRequest("Fabricante", null, true)))
                .isInstanceOf(ConflictException.class);
    }
}
