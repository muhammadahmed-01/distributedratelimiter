package com.example.DistributedRateLimiter.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * CLI utility to generate a signed JWT for local testing.
 * Usage: mvn -q exec:java -Dexec.mainClass=com.example.DistributedRateLimiter.security.JwtGen
 * Set JWT_SIGNING_KEY env var to match application configuration.
 */
public class JwtGen {
    public static void main(String[] args) {
        String rawKey = System.getenv().getOrDefault(
                "JWT_SIGNING_KEY",
                "my-super-secret-signing-key-which-must-be-32-bytes!"
        );

        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);

        String token = Jwts.builder()
                .setSubject("user123")
                .addClaims(Map.of(
                        "accountId", "acc-001",
                        "userId", "u-123"
                ))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000 * 24 * 7))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();

        System.out.println("\nGenerated JWT:\n" + token + "\n");
    }
}
