package com.example.DistributedRateLimiter.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonErrorWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonErrorWriter() {
    }

    public static void write(HttpServletResponse response, int status, Map<String, String> fields) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(response.getWriter(), fields);
    }

    public static void writeError(HttpServletResponse response, int status, String error, String correlationId) throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", error);
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }
        write(response, status, body);
    }

    public static void writeRateLimited(HttpServletResponse response, String type, String correlationId) throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "rate_limited");
        body.put("type", type);
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }
        write(response, 429, body);
    }
}
