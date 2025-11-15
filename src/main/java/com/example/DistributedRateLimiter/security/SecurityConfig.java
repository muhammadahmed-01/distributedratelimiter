package com.example.DistributedRateLimiter.security;

import com.example.DistributedRateLimiter.filter.IpRateLimitFilter;
import com.example.DistributedRateLimiter.filter.AccountRateLimitFilter;
import com.example.DistributedRateLimiter.filter.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final IpRateLimitFilter ipRateLimitFilter;
    private final JwtAuthFilter jwtAuthFilter;
    private final AccountRateLimitFilter accountRateLimitFilter;

    public SecurityConfig(IpRateLimitFilter ipRateLimitFilter, JwtAuthFilter jwtAuthFilter, AccountRateLimitFilter accountRateLimitFilter) {
        this.ipRateLimitFilter = ipRateLimitFilter;
        this.jwtAuthFilter = jwtAuthFilter;
        this.accountRateLimitFilter = accountRateLimitFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll() // allow actuator for debugging
                        .anyRequest().authenticated()
                )
//                // 1️⃣ IP limiter runs first
//                .addFilterBefore(ipRateLimitFilter, BasicAuthenticationFilter.class)
                // 2️⃣ JWT filter sets SecurityContext / accountId
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // 3️⃣ Account-level limiter runs after JWT
                .addFilterAfter(accountRateLimitFilter, JwtAuthFilter.class)
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
