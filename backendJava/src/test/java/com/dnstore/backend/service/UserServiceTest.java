package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.repository.StockEntryRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import com.dnstore.backend.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StockEntryRepository stockEntryRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @InjectMocks
    private UserService service;

    private final UUID id = UUID.randomUUID();

    @Test
    void delete_whenUserHasNoStockHistory_shouldDeleteAsBefore() {
        when(userRepository.existsById(id)).thenReturn(true);

        assertThat(service.delete(id)).isTrue();
        verify(userRepository).deleteById(id);
    }

    @Test
    void delete_whenUserDoesNotExist_shouldReturnFalse() {
        when(userRepository.existsById(id)).thenReturn(false);

        assertThat(service.delete(id)).isFalse();
        verify(userRepository, never()).deleteById(id);
    }

    @Test
    void delete_whenUserRegisteredAnEntryNote_shouldConflictWithAClearMessage() {
        when(userRepository.existsById(id)).thenReturn(true);
        when(stockEntryRepository.existsByCreatedBy_Id(id)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("notas de entrada")
                .hasMessageContaining("altere o papel");
        verify(userRepository, never()).deleteById(id);
    }

    @Test
    void delete_whenUserIsTheActorOfAMovement_shouldConflict() {
        when(userRepository.existsById(id)).thenReturn(true);
        when(stockEntryRepository.existsByCreatedBy_Id(id)).thenReturn(false);
        when(stockMovementRepository.existsByActor_Id(id)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ConflictException.class);
        verify(userRepository, never()).deleteById(id);
    }
}
