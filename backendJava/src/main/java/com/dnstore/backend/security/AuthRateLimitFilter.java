package com.dnstore.backend.security;

import com.dnstore.backend.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas por IP nas rotas públicas de autenticação (login, cadastro, códigos e
 * recuperação de senha) para dificultar força bruta e abuso de envio de e-mail.
 *
 * O contador fica em memória: o backend roda em instância única (há tarefas agendadas), e o
 * contador reinicia junto com o processo. O IP vem de {@code getRemoteAddr()}, já resolvido a partir
 * de X-Forwarded-For pelo Tomcat nos perfis staging/prod (ver application-prod.properties).
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "/api/auth/";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String CHANGE_PASSWORD_PATH = "/api/auth/change-password";
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final ObjectMapper objectMapper;
    private final int loginLimit;
    private final int generalLimit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${auth.rate-limit.login:10}") int loginLimit,
            @Value("${auth.rate-limit.general:30}") int generalLimit,
            @Value("${auth.rate-limit.window:PT5M}") Duration window) {
        this(objectMapper, loginLimit, generalLimit, window, Clock.systemUTC());
    }

    AuthRateLimitFilter(ObjectMapper objectMapper, int loginLimit, int generalLimit, Duration window, Clock clock) {
        this.objectMapper = objectMapper;
        this.loginLimit = loginLimit;
        this.generalLimit = generalLimit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"POST".equals(request.getMethod())
                || !path.startsWith(AUTH_PREFIX)
                || path.equals(CHANGE_PASSWORD_PATH);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        boolean login = request.getRequestURI().equals(LOGIN_PATH);
        String key = (login ? "login:" : "auth:") + request.getRemoteAddr();
        long retryAfterSeconds = tryAcquire(key, login ? loginLimit : generalLimit);

        if (retryAfterSeconds > 0) {
            logger.warn("security.rate_limit.blocked bucket=" + (login ? "login" : "auth")
                    + " ip=" + request.getRemoteAddr());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Aguarde alguns minutos e tente novamente.",
                    request.getRequestURI()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Retorna 0 se a tentativa foi aceita, ou os segundos até o fim da janela se foi bloqueada. */
    private long tryAcquire(String key, int limit) {
        long now = clock.millis();
        evictIfTooLarge(now);
        long[] retryAfter = {0};
        windows.compute(key, (k, window) -> {
            if (window == null || now - window.start >= windowMillis) {
                return new Window(now, 1);
            }
            if (window.count >= limit) {
                retryAfter[0] = Math.max(1, (window.start + windowMillis - now + 999) / 1000);
                return window;
            }
            return new Window(window.start, window.count + 1);
        });
        return retryAfter[0];
    }

    private void evictIfTooLarge(long now) {
        if (windows.size() < MAX_TRACKED_CLIENTS) {
            return;
        }
        windows.entrySet().removeIf(entry -> now - entry.getValue().start >= windowMillis);
        if (windows.size() >= MAX_TRACKED_CLIENTS) {
            // Proteção contra esgotar memória: prefere liberar o limite a derrubar o serviço.
            windows.clear();
        }
    }

    private record Window(long start, int count) {
    }
}
