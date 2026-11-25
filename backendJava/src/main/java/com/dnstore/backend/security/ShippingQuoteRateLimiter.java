package com.dnstore.backend.security;

import com.dnstore.backend.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita as cotações de frete por cliente autenticado. Cada cotação pode chamar o Melhor Envio e
 * gravar linhas no banco, então um único usuário não deve conseguir esgotar a cota do provedor.
 * O fluxo normal (digitar o CEP, eventualmente corrigir e cotar de novo) usa poucas requisições
 * por minuto; o padrão de 10 por minuto não atrapalha um cliente real.
 *
 * <p><b>Premissa: instância única.</b> O contador fica em memória, como o {@link AuthRateLimitFilter},
 * e isto tem consequências conhecidas:
 * <ul>
 *   <li>com N instâncias o limite efetivo por cliente passa a ser até N vezes o configurado, pois cada
 *       instância conta sozinha;</li>
 *   <li>reiniciar o processo (inclusive no deploy, em que duas instâncias podem coexistir por instantes)
 *       zera os contadores;</li>
 *   <li>não há limite global: muitos clientes diferentes cotando ao mesmo tempo não são contidos aqui. A
 *       proteção da cota do provedor nesse caso vem do reaproveitamento de cotações recentes e do
 *       tratamento de 429 do Melhor Envio.</li>
 * </ul>
 * Se o backend passar a ter mais de uma instância, troque este componente por um contador
 * compartilhado (Redis ou tabela) antes de escalar.
 */
@Component
public class ShippingQuoteRateLimiter {

    private static final int MAX_TRACKED_USERS = 10_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public ShippingQuoteRateLimiter(
            @Value("${shipping.quote-rate-limit.requests:10}") int limit,
            @Value("${shipping.quote-rate-limit.window:PT1M}") Duration window) {
        this(limit, window, Clock.systemUTC());
    }

    ShippingQuoteRateLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("Shipping quote rate limit must be positive.");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** Registra uma tentativa; lança {@link RateLimitExceededException} se o limite da janela foi atingido. */
    public void acquire(UUID userId) {
        long now = clock.millis();
        evictIfTooLarge(now);
        long[] retryAfter = {0};
        windows.compute(userId, (key, window) -> {
            if (window == null || now - window.start >= windowMillis) {
                return new Window(now, 1);
            }
            if (window.count >= limit) {
                retryAfter[0] = Math.max(1, (window.start + windowMillis - now + 999) / 1000);
                return window;
            }
            return new Window(window.start, window.count + 1);
        });
        if (retryAfter[0] > 0) {
            throw new RateLimitExceededException(
                    "Muitas consultas de frete. Aguarde alguns segundos e tente novamente.", retryAfter[0]);
        }
    }

    private void evictIfTooLarge(long now) {
        if (windows.size() < MAX_TRACKED_USERS) {
            return;
        }
        windows.entrySet().removeIf(entry -> now - entry.getValue().start >= windowMillis);
        if (windows.size() >= MAX_TRACKED_USERS) {
            windows.clear();
        }
    }

    private record Window(long start, int count) {
    }
}
