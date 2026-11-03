package com.dnstore.backend.service;

import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.repository.FavoriteRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void findByUser_returnsOnlyFavoritesForActiveProducts() {
        UUID userId = UUID.randomUUID();
        when(favoriteRepository.findActiveProductsByUserId(userId)).thenReturn(List.of());

        assertThat(favoriteService.findByUser(userId)).isEmpty();

        verify(favoriteRepository).findActiveProductsByUserId(userId);
        verify(favoriteRepository, never()).findByUserId(userId);
    }

    @Test
    void add_whenProductIsInactive_rejectsFavorite() {
        UUID productId = UUID.randomUUID();
        Product product = mock(Product.class);
        when(product.isActive()).thenReturn(false);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> favoriteService.add(UUID.randomUUID(), productId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto não encontrado.");

        verifyNoInteractions(userRepository, favoriteRepository);
    }
}
