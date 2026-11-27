package com.dnstore.backend.service;

import com.dnstore.backend.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** E-mail do admin autenticado, para o campo performed_by dos movimentos que não vêm de um controller de estoque. */
final class StockActor {

    private StockActor() {
    }

    static String current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getEmail();
        }
        return "system";
    }
}
