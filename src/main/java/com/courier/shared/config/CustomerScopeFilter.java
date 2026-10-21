package com.courier.shared.config;

import com.courier.shared.api.ApiResponse;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Keeps customer-portal accounts inside the customer portal.
 *
 * <p>A principal holding role {@code CUSTOMER} may call only the customer-portal API and the
 * auth endpoints (refresh, logout, change password, me). Everything else answers 403 here,
 * before any controller — many staff services read with {@code isAuthenticated()}, so without
 * this gate a customer token could list the company's customers, shipments and rates.
 * Fail-closed: a token that carries {@code CUSTOMER} together with any other role is gated too.
 *
 * <p>Not a {@code @Component}: built by {@link SecurityConfig} and placed in the security chain
 * only, after the JWT filter, so it sees the verified principal.
 */
public class CustomerScopeFilter extends OncePerRequestFilter {

    static final List<String> CUSTOMER_ALLOWED_PREFIXES = List.of(
            "/api/v1/customer-portal/",
            "/api/v1/auth/");

    private final ObjectMapper objectMapper;

    public CustomerScopeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isCustomer() && !allowed(request.getRequestURI())) {
            response.setStatus(ErrorCode.ACCESS_DENIED.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(ErrorCode.ACCESS_DENIED,
                    "This account can only use the customer portal.", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.roles() != null
                && user.roles().contains(Roles.CUSTOMER);
    }

    static boolean allowed(String uri) {
        if (uri == null) {
            return false;
        }
        // Reject path tricks outright rather than trying to normalise them.
        if (uri.contains("..") || uri.contains("//") || uri.contains(";") || uri.contains("%")) {
            return false;
        }
        return CUSTOMER_ALLOWED_PREFIXES.stream().anyMatch(uri::startsWith);
    }
}
