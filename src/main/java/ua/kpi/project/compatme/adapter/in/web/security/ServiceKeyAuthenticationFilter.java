package ua.kpi.project.compatme.adapter.in.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authenticates trusted service-to-service calls (the Telegram bot) via the {@code X-Service-Key}
 * header. A present-but-wrong key is rejected outright with 401 (never falls through as
 * anonymous). The key is compared in constant time.
 */
public class ServiceKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Key";
    public static final String ROLE = "ROLE_SERVICE";

    private final byte[] expectedKey;
    private final AuthenticationEntryPoint entryPoint;

    public ServiceKeyAuthenticationFilter(String serviceKey, AuthenticationEntryPoint entryPoint) {
        this.expectedKey = serviceKey.getBytes(StandardCharsets.UTF_8);
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("Invalid service key"));
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "service", null, AuthorityUtils.createAuthorityList(ROLE)));
        chain.doFilter(request, response);
    }
}
