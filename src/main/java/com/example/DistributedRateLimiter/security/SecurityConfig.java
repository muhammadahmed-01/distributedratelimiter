package com.example.DistributedRateLimiter.security;

import com.example.DistributedRateLimiter.filter.AccountRateLimitFilter;
import com.example.DistributedRateLimiter.filter.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Profile("!prod")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http,
                                                      JwtAuthFilter jwtAuthFilter,
                                                      AccountRateLimitFilter accountRateLimitFilter) throws Exception {
        return buildChain(http, jwtAuthFilter, accountRateLimitFilter, true);
    }

    @Bean
    @Profile("prod")
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http,
                                                       JwtAuthFilter jwtAuthFilter,
                                                       AccountRateLimitFilter accountRateLimitFilter) throws Exception {
        return buildChain(http, jwtAuthFilter, accountRateLimitFilter, false);
    }

    private SecurityFilterChain buildChain(HttpSecurity http,
                                           JwtAuthFilter jwtAuthFilter,
                                           AccountRateLimitFilter accountRateLimitFilter,
                                           boolean permitActuatorMetrics) throws Exception {
        var auth = http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(registry -> {
                    registry.requestMatchers(EndpointRequest.to("health")).permitAll();
                    registry.requestMatchers(EndpointRequest.to("info")).permitAll();
                    if (permitActuatorMetrics) {
                        registry.requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll();
                        registry.requestMatchers("/actuator/**").permitAll();
                    } else {
                        registry.requestMatchers("/actuator/prometheus").denyAll();
                        registry.requestMatchers(EndpointRequest.to("prometheus")).denyAll();
                    }
                    registry.anyRequest().permitAll();
                })
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(accountRateLimitFilter, JwtAuthFilter.class);

        return auth.build();
    }
}
