package com.dnstore.backend.model;

import java.util.Set;

public final class ProductVariantSizes {

    public static final Set<String> ALLOWED = Set.of(
            "PP", "P", "M", "G", "GG", "BL P", "BL M", "BL G", "Único");

    private ProductVariantSizes() {
    }
}
