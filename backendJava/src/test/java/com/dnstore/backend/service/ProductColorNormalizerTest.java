package com.dnstore.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductColorNormalizerTest {

    @Test
    void normalizesPortugueseGenderAndEnglishAliases() {
        assertEquals("Preto", ProductColorNormalizer.normalize(" PRETA "));
        assertEquals("Preto", ProductColorNormalizer.normalize("black"));
        assertEquals("Amarelo", ProductColorNormalizer.normalize("amarela"));
    }

    @Test
    void normalizesAndOrdersCombinedColors() {
        assertEquals("Preto e Vermelho", ProductColorNormalizer.normalize("vermelha & preta"));
    }

    @Test
    void rejectsValuesThatAreNotKnownColors() {
        assertThrows(IllegalArgumentException.class, () -> ProductColorNormalizer.normalize("metalizado xyz"));
    }

    @Test
    void keepsAnEmptyColorOptional() {
        assertEquals(null, ProductColorNormalizer.normalize("  "));
    }
}
