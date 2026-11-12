package gestion_polizas.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class ApiKeyFilter extends OncePerRequestFilter {
    private final byte[] expectedKey;
    private final ApiSecurityErrorHandler errorHandler;

    ApiKeyFilter(String apiKey, ApiSecurityErrorHandler errorHandler) {
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String suppliedKey = request.getHeader("x-api-key");
        if (suppliedKey == null || !MessageDigest.isEqual(expectedKey,
                suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            errorHandler.commence(request, response, null);
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "api-client", null, List.of()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
