package com.example.DistributedRateLimiter.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtGen {
    public static void main(String[] args) {
        // Paste your raw signing key here (NOT Base64)
        String rawKey = "my-super-secret-signing-key-which-must-be-32-bytes!";

        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);

        String token = Jwts.builder()
                .setSubject("user123")
                .addClaims(Map.of(
                        "accountId", "acc-001",
                        "userId", "u-123"
                ))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000 * 24 * 7)) // 1 week
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();

        System.out.println("\nGenerated JWT:\n" + token + "\n");
    }
}
