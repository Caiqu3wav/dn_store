package com.dnstore.backend.service;

import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Livro real sobre repositório em memória, para testes unitários que precisam do saldo de fato alterado. */
final class TestStockLedger {

    private TestStockLedger() {
    }

    static StockLedger create(List<StockMovement> sink) {
        StockMovementRepository repository = mock(StockMovementRepository.class);
        lenient().when(repository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            sink.add(movement);
            return movement;
        });
        EntityManager entityManager = mock(EntityManager.class);
        lenient().when(entityManager.getLockMode(any())).thenReturn(LockModeType.PESSIMISTIC_WRITE);
        return new StockLedger(repository, entityManager);
    }
}
