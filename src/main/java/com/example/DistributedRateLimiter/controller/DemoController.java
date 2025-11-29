package com.example.DistributedRateLimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @GetMapping("/api/hello")
    public String hello() {
        return "✅ Access granted: You passed IP + JWT filters!";
    }
}
