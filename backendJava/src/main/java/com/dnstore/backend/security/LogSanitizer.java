package com.dnstore.backend.security;

/**
 * Evita registrar dados pessoais completos nos logs e impede que valores vindos do cliente
 * forjem linhas de log (quebras de linha).
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /** "maria.silva@gmail.com" -> "m***@gmail.com". */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "[none]";
        }
        String clean = email.trim().replaceAll("\\p{Cntrl}", "");
        int at = clean.lastIndexOf('@');
        if (at < 0) {
            return "***";
        }
        String domain = clean.substring(at);
        return (at == 0 ? "" : clean.substring(0, 1)) + "***" + domain;
    }
}
