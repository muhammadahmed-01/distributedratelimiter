package com.example.DistributedRateLimiter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

@Service
public class JwtSecretService {

    private final SecretsManagerClient secretsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtSecretService(SecretsManagerClient secretsClient) {
        this.secretsClient = secretsClient;
    }

    public String getSigningKey() {
        String secretName = "qa/distributed-rate-limiter";

        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse response = secretsClient.getSecretValue(request);

        String secretString = response.secretString();
        if (secretString == null || secretString.isEmpty()) {
            throw new IllegalStateException("Secrets Manager returned empty secret for " + secretName);
        }

        try {
            // Parse the JSON and get the key
            JsonNode root = objectMapper.readTree(secretString);
            return root.get("myJwtSigningKey").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JWT signing key from Secrets Manager", e);
        }
    }
}
