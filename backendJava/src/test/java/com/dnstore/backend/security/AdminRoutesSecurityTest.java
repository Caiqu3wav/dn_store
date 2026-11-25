package com.dnstore.backend.security;

import com.dnstore.backend.config.SecurityConfiguration;
import com.dnstore.backend.repository.UserRepository;
import com.dnstore.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * As rotas administrativas precisam exigir ADMIN já na regra de URL, sem depender só de
 * {@code @PreAuthorize} nos controllers. O controller stub responde 200 a qualquer rota.
 */
@WebMvcTest(controllers = AdminRoutesSecurityTest.AnyRoute.class)
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, AuthRateLimitFilter.class,
        ApiSecurityExceptionHandler.class, AdminRoutesSecurityTest.AnyRoute.class})
class AdminRoutesSecurityTest {

    @RestController
    @RequestMapping("/**")
    static class AnyRoute {
        @RequestMapping
        String ok() {
            return "ok";
        }
    }

    private static final String[][] ADMIN_ONLY = {
            {"GET", "/api/products/admin"},
            {"GET", "/api/products/admin/123"},
            {"GET", "/api/products/admin/default-rules"},
            {"POST", "/api/products"},
            {"POST", "/api/products/batch"},
            {"PUT", "/api/products/123"},
            {"DELETE", "/api/products/123"},
            {"POST", "/api/categories"},
            {"PUT", "/api/categories/123"},
            {"DELETE", "/api/categories/123"},
            {"GET", "/api/events/all"},
            {"POST", "/api/events"},
            {"PUT", "/api/events/123"},
            {"DELETE", "/api/events/123"},
            {"GET", "/api/orders/all"},
            {"PUT", "/api/orders/123/status"},
            {"POST", "/api/orders/123/confirm-payment"},
            {"GET", "/api/orders/123/status-audit"},
            {"GET", "/api/users"},
            {"PUT", "/api/users/123/role"},
            {"DELETE", "/api/users/123"},
            {"GET", "/api/admin/dashboard"},
            {"GET", "/api/admin/suppliers"},
            {"POST", "/api/admin/suppliers"},
            {"PUT", "/api/admin/suppliers/123"},
            {"POST", "/api/admin/stock/entries"},
            {"GET", "/api/admin/stock/entries"},
            {"GET", "/api/admin/stock/entries/123"},
            {"POST", "/api/admin/stock/adjustments"},
            {"GET", "/api/admin/stock/movements"},
            {"GET", "/api/admin/stock/variants"},
            {"GET", "/api/admin/stock/sales-outflows"},
            {"GET", "/api/admin/stock/reconciliation"},
            {"GET", "/api/integrations/melhor-envio/authorize"},
            {"GET", "/api/integrations/melhor-envio/status"},
    };

    private static final String[][] PUBLIC = {
            {"GET", "/api/products"},
            {"GET", "/api/products/123"},
            {"GET", "/api/categories"},
            {"GET", "/api/events"},
            {"GET", "/api/events/123"},
            {"GET", "/api/integrations/melhor-envio/callback"},
    };

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private int statusOf(String[] route) throws Exception {
        return mockMvc.perform(request(HttpMethod.valueOf(route[0]), route[1])).andReturn().getResponse().getStatus();
    }

    @Test
    void adminRoutes_shouldRejectAnonymousWith401() throws Exception {
        for (String[] route : ADMIN_ONLY) {
            org.assertj.core.api.Assertions.assertThat(statusOf(route))
                    .as("%s %s (anônimo)", route[0], route[1]).isEqualTo(401);
        }
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminRoutes_shouldRejectRegularUsersWith403() throws Exception {
        for (String[] route : ADMIN_ONLY) {
            org.assertj.core.api.Assertions.assertThat(statusOf(route))
                    .as("%s %s (USER)", route[0], route[1]).isEqualTo(403);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRoutes_shouldAllowAdmins() throws Exception {
        for (String[] route : ADMIN_ONLY) {
            org.assertj.core.api.Assertions.assertThat(statusOf(route))
                    .as("%s %s (ADMIN)", route[0], route[1]).isEqualTo(200);
        }
    }

    @Test
    void catalogReads_shouldStayPublic() throws Exception {
        for (String[] route : PUBLIC) {
            org.assertj.core.api.Assertions.assertThat(statusOf(route))
                    .as("%s %s (anônimo)", route[0], route[1]).isEqualTo(200);
        }
    }
}
