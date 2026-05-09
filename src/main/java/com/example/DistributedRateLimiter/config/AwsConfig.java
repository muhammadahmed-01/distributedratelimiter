package com.example.DistributedRateLimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration
public class AwsConfig {

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        // .create() uses the DefaultCredentialsProvider and DefaultRegionProvider
        // which automatically picks up AWS_PROFILE and AWS_REGION from environment variables
        // set by IntelliJ's AWS Connection plugin or Docker Compose.
        return SecretsManagerClient.create();
    }
}
