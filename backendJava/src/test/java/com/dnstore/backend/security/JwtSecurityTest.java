package com.dnstore.backend.security;

import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.repository.UserRepository;
import com.dnstore.backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtService_shouldRespectTwentyFourHourMaximumLifetime() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "c2VjcmV0LWRldi1kbi1zdG9yZS1jaGF2ZS1sb2NhbC0yMDI1");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86_400_000L);

        User user = new User();
        user.setEmail("admin@dnstore.com");
        user.setRole(Role.ADMIN);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.getExpirationTime()).isLessThanOrEqualTo(86_400_000L);
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin@dnstore.com");
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void jwtService_shouldRejectExpiredToken() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "c2VjcmV0LWRldi1kbi1zdG9yZS1jaGF2ZS1sb2NhbC0yMDI1");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1L);

        User user = new User();
        user.setEmail("user@dnstore.com");
        user.setRole(Role.USER);

        String expiredToken = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(expiredToken, user)).isFalse();
    }

    @Test
    void userRole_shouldMapToRoleAdminAuthority() {
        User user = new User();
        user.setEmail("admin@dnstore.com");
        user.setRole(Role.ADMIN);

        assertThat(user.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void jwtFilter_shouldRejectExpiredTokenAndLeaveContextEmpty() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);

        String token = "expired-token";
        User user = new User();
        user.setEmail("admin@dnstore.com");
        user.setRole(Role.ADMIN);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn("admin@dnstore.com");
        when(userRepository.findByEmail("admin@dnstore.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(token, user)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
