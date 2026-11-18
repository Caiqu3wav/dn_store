package com.dnstore.backend.config;

import com.dnstore.backend.model.User;
import com.dnstore.backend.security.ShippingQuoteRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Aplica o limite de cotações de frete depois da autenticação (o limite é por usuário), sem mexer
 * nos controladores. Cobre o endpoint do checkout e o endpoint legado {@code GET /api/shipping/quote}.
 */
@Configuration
@RequiredArgsConstructor
public class ShippingQuoteRateLimitConfiguration implements WebMvcConfigurer {

    // ObjectProvider: fatias de teste @WebMvcTest carregam este WebMvcConfigurer sem o limitador.
    private final ObjectProvider<ShippingQuoteRateLimiter> rateLimiterProvider;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        ShippingQuoteRateLimiter rateLimiter = rateLimiterProvider.getIfAvailable();
        if (rateLimiter == null) {
            return;
        }
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof User user
                        && user.getId() != null) {
                    rateLimiter.acquire(user.getId());
                }
                return true;
            }
        }).addPathPatterns("/api/orders/shipping-quotes", "/api/shipping/quote");
    }
}
