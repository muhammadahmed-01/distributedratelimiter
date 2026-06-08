package com.example.DistributedRateLimiter.filter;

import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import com.example.DistributedRateLimiter.util.JsonErrorWriter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtParser jwtParser;
    private final RateLimitMetrics metrics;

    public JwtAuthFilter(RateLimitMetrics metrics, String signingKey) {
        this.metrics = metrics;
        this.jwtParser = Jwts.parser()
                .setSigningKey(Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
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

            List<GrantedAuthority> authorities = Collections.emptyList();
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(subject, null, authorities);
            authToken.setDetails(Map.of("accountId", accountId, "userId", userId));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            request.setAttribute("accountId", accountId);
            request.setAttribute("userId", userId);

            log.debug("JWT validated for accountId={}", accountId);
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            metrics.recordInvalid("jwt");
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_LOG_VAR);
            log.warn("Invalid JWT correlationId={}: {}", correlationId, e.getMessage());
            JsonErrorWriter.writeError(response, HttpStatus.UNAUTHORIZED.value(), "invalid_token", correlationId);
        }
    }
}
