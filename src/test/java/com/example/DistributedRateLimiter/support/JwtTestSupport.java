package com.example.DistributedRateLimiter.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public final class JwtTestSupport {

    public static final String TEST_SIGNING_KEY = "my-super-secret-signing-key-which-must-be-32-bytes!";

    private JwtTestSupport() {
    }

    public static String tokenFor(String accountId, String userId) {
        return Jwts.builder()
                .setSubject("user123")
                .addClaims(Map.of("accountId", accountId, "userId", userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SIGNING_KEY.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
