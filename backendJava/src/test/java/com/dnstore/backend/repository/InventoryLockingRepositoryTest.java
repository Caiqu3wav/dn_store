package com.dnstore.backend.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryLockingRepositoryTest {

    @Test
    void variantInventoryMutations_usePessimisticWriteLocks() throws NoSuchMethodException {
        assertWriteLock(ProductVariantRepository.class.getMethod("findByIdForUpdate", java.util.UUID.class));
        assertWriteLock(ProductVariantRepository.class.getMethod(
                "findAllByIdInForUpdate", java.util.Collection.class));
    }

    @Test
    void cartCheckoutUsesPessimisticWriteLock() throws NoSuchMethodException {
        Method method = CartRepository.class.getMethod("findByUserForUpdate", com.dnstore.backend.model.User.class);
        assertWriteLock(method);
    }

    private void assertWriteLock(Method method) {
        Lock lock = method.getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
