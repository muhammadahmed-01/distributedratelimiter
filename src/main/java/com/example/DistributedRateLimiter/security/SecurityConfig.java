package com.example.DistributedRateLimiter.security;

import com.example.DistributedRateLimiter.IpRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final IpRateLimitFilter ipRateLimitFilter;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(IpRateLimitFilter ipRateLimitFilter, JwtAuthFilter jwtAuthFilter) {
        this.ipRateLimitFilter = ipRateLimitFilter;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll() // allow actuator for debugging
                        .anyRequest().authenticated()
                )
                .addFilterBefore(ipRateLimitFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
