package com.dnstore.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void maskEmail_shouldKeepFirstCharacterAndDomain() {
        assertThat(LogSanitizer.maskEmail("maria.silva@gmail.com")).isEqualTo("m***@gmail.com");
    }

    @Test
    void maskEmail_shouldHandleMissingOrMalformedValues() {
        assertThat(LogSanitizer.maskEmail(null)).isEqualTo("[none]");
        assertThat(LogSanitizer.maskEmail("  ")).isEqualTo("[none]");
        assertThat(LogSanitizer.maskEmail("sem-arroba")).isEqualTo("***");
        assertThat(LogSanitizer.maskEmail("@dominio.com")).isEqualTo("***@dominio.com");
    }

    @Test
    void maskEmail_shouldNeverEmitControlCharactersThatCouldForgeLogLines() {
        String masked = LogSanitizer.maskEmail("a\nINFO fake line@x.com\r");

        assertThat(masked).doesNotContain("\n").doesNotContain("\r");
    }
}
