package com.metro.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
public class TestController {
    
    @GetMapping("/")
    public Map<String, String> test() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Metro Backend is running!");
        return response;
    }
}