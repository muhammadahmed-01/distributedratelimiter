package com.example.DistributedRateLimiter.filter;

import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private JwtParser jwtParser; // configure with signing key elsewhere
    private final RateLimitMetrics metrics;

    @Value("${jwt.signingKey}")
    private String signingKey;

    public JwtAuthFilter(RateLimitMetrics metrics) {
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        this.jwtParser = Jwts.parser()
                .setSigningKey(Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
//        System.out.println("[JwtAuthFilter] Checking for Authorization header");
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = auth.substring(7);
        try {
            Jws<Claims> jws = jwtParser.parseClaimsJws(token);
            Claims claims = jws.getBody();

            String accountId = claims.get("accountId", String.class);
            String userId = claims.get("userId", String.class);
            String subject = claims.getSubject();

            // Put into SecurityContext (so other Spring machinery can use it)
            List<GrantedAuthority> authorities = Collections.emptyList();
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(subject, null, authorities);
            authToken.setDetails(Map.of("accountId", accountId, "userId", userId));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            // Also set as request attributes for your rate-limit filter/service
            request.setAttribute("accountId", accountId);
            request.setAttribute("userId", userId);

            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            metrics.recordInvalid("jwt");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_LOG_VAR);
            response.getWriter().write(String.format("{\"error\":\"invalid_token\", \"correlationId\":\"%s\"}", correlationId));
            System.out.println("[JwtAuthFilter] Invalid JWT: " + e.getMessage());
        }
    }
}
