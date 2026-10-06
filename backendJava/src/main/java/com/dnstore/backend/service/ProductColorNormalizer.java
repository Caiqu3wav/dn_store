package com.dnstore.backend.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class ProductColorNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATOR = Pattern.compile("\\s+(?:e|and)\\s+|\\s*[&+/;,]\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_SPACING = Pattern.compile("[-_]+|\\s+");
        private static final List<String> COLOR_ORDER = List.of(
            "Preto", "Branco", "Amarelo", "Vermelho", "Azul", "Azul-marinho", "Verde", "Laranja",
            "Rosa", "Marrom", "Cinza", "Roxo", "Bege", "Dourado", "Prata", "Vinho", "Transparente",
            "Multicolorido", "Turquesa", "Ciano", "Magenta", "Lilás", "Salmão", "Coral", "Caramelo",
            "Creme", "Cáqui", "Cobre", "Bronze", "Grafite", "Chumbo", "Verde-oliva", "Verde-petróleo",
            "Índigo", "Mostarda", "Azul-claro", "Azul-escuro", "Verde-claro", "Verde-escuro"
        );
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("preto", "Preto"), Map.entry("preta", "Preto"), Map.entry("black", "Preto"),
            Map.entry("branco", "Branco"), Map.entry("branca", "Branco"), Map.entry("white", "Branco"),
            Map.entry("amarelo", "Amarelo"), Map.entry("amarela", "Amarelo"), Map.entry("yellow", "Amarelo"),
            Map.entry("vermelho", "Vermelho"), Map.entry("vermelha", "Vermelho"), Map.entry("red", "Vermelho"),
            Map.entry("azul", "Azul"), Map.entry("blue", "Azul"),
            Map.entry("verde", "Verde"), Map.entry("green", "Verde"),
            Map.entry("cinza", "Cinza"), Map.entry("cinzento", "Cinza"), Map.entry("cinzenta", "Cinza"),
            Map.entry("gray", "Cinza"), Map.entry("grey", "Cinza"),
            Map.entry("laranja", "Laranja"), Map.entry("orange", "Laranja"),
            Map.entry("rosa", "Rosa"), Map.entry("pink", "Rosa"),
            Map.entry("marrom", "Marrom"), Map.entry("brown", "Marrom"),
            Map.entry("roxo", "Roxo"), Map.entry("roxa", "Roxo"), Map.entry("purple", "Roxo"),
            Map.entry("violeta", "Roxo"), Map.entry("violet", "Roxo"),
            Map.entry("bege", "Bege"), Map.entry("beige", "Bege"),
            Map.entry("dourado", "Dourado"), Map.entry("dourada", "Dourado"), Map.entry("gold", "Dourado"),
            Map.entry("prata", "Prata"), Map.entry("prateado", "Prata"), Map.entry("prateada", "Prata"),
            Map.entry("silver", "Prata"),
            Map.entry("azul marinho", "Azul-marinho"), Map.entry("marinho", "Azul-marinho"),
            Map.entry("navy", "Azul-marinho"), Map.entry("navy blue", "Azul-marinho"),
            Map.entry("vinho", "Vinho"), Map.entry("burgundy", "Vinho"),
            Map.entry("transparente", "Transparente"), Map.entry("transparent", "Transparente"),
            Map.entry("multicolorido", "Multicolorido"), Map.entry("multicolorida", "Multicolorido"),
            Map.entry("multicolor", "Multicolorido"),
            Map.entry("turquesa", "Turquesa"), Map.entry("turquoise", "Turquesa"),
            Map.entry("ciano", "Ciano"), Map.entry("cyan", "Ciano"), Map.entry("magenta", "Magenta"),
            Map.entry("lilas", "Lilás"), Map.entry("lilases", "Lilás"), Map.entry("lavender", "Lilás"), Map.entry("lilac", "Lilás"),
            Map.entry("salmao", "Salmão"), Map.entry("salmon", "Salmão"), Map.entry("coral", "Coral"),
            Map.entry("caramelo", "Caramelo"), Map.entry("caramel", "Caramelo"),
            Map.entry("creme", "Creme"), Map.entry("cream", "Creme"),
            Map.entry("caqui", "Cáqui"), Map.entry("khaki", "Cáqui"),
            Map.entry("cobre", "Cobre"), Map.entry("copper", "Cobre"), Map.entry("bronze", "Bronze"),
            Map.entry("grafite", "Grafite"), Map.entry("graphite", "Grafite"), Map.entry("chumbo", "Chumbo"),
            Map.entry("verde oliva", "Verde-oliva"), Map.entry("oliva", "Verde-oliva"), Map.entry("olive", "Verde-oliva"),
            Map.entry("verde petroleo", "Verde-petróleo"), Map.entry("petroleo", "Verde-petróleo"), Map.entry("teal", "Verde-petróleo"),
            Map.entry("indigo", "Índigo"), Map.entry("mostarda", "Mostarda"), Map.entry("mustard", "Mostarda"),
            Map.entry("azul claro", "Azul-claro"), Map.entry("light blue", "Azul-claro"),
            Map.entry("azul escuro", "Azul-escuro"), Map.entry("dark blue", "Azul-escuro"),
            Map.entry("verde claro", "Verde-claro"), Map.entry("light green", "Verde-claro"),
            Map.entry("verde escuro", "Verde-escuro"), Map.entry("dark green", "Verde-escuro")
    );

    private ProductColorNormalizer() {}

    public static String normalize(String input) {
        if (input == null || input.isBlank()) return null;

        String[] parts = SEPARATOR.split(input.trim());
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String part : parts) {
            String key = normalizeToken(part);
            String canonical = ALIASES.get(key);
            if (canonical == null) {
                throw new IllegalArgumentException("Cor inválida: " + part.trim());
            }
            normalized.add(canonical);
        }
        List<String> ordered = new ArrayList<>(normalized);
        ordered.sort((left, right) -> Integer.compare(COLOR_ORDER.indexOf(left), COLOR_ORDER.indexOf(right)));
        return String.join(" e ", ordered);
    }

    private static String normalizeToken(String value) {
        String withoutAccents = DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        return TOKEN_SPACING.matcher(withoutAccents.toLowerCase(Locale.ROOT).trim()).replaceAll(" ").trim();
    }
}