package com.metro.controller;

import cn.hutool.extra.qrcode.QrCodeUtil;
import com.metro.entity.MetroTravel;
import com.metro.service.QRCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public class QRCodeController {

    @Autowired
    private QRCodeService qrCodeService;

    @PostMapping("/api/qrcode/scan")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> scanQRCode(
            @RequestParam String qrContent,
            @RequestParam String station) {
        
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> response = new HashMap<>();
            try {
                String userId = extractUserIdFromQRCode(qrContent);
                if (userId == null) {
                    throw new IllegalArgumentException("无效的二维码内容");
                }
                
                MetroTravel travel = qrCodeService.processStation(userId, station);
                
                response.put("success", true);
                response.put("message", "COMPLETED".equals(travel.getStatus()) ? "行程已完成" : "行程已开始");
                response.put("status", travel.getStatus());
                response.put("startStation", travel.getStartStation());
                response.put("endStation", travel.getEndStation());
                response.put("userId", userId);
                
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                response.put("success", false);
                response.put("message", "扫描处理失败: " + e.getMessage());
                return ResponseEntity.badRequest().body(response);
            }
        });
    }

    private String extractUserIdFromQRCode(String qrContent) {
        if (qrContent == null || !qrContent.startsWith("METRO_USER_")) {
            return null;
        }
        String[] parts = qrContent.split("_");
        if (parts.length < 6) {  // 增加 nonce 验证
            return null;
        }
        
        try {
            String userId = parts[2];
            long timestamp = Long.parseLong(parts[3]);
            String nonce = parts[4];
            String signature = parts[5];
            long currentTime = System.currentTimeMillis();
            
            // 验证时间戳
            if (currentTime - timestamp > 3000) {
                throw new IllegalArgumentException("二维码已过期");
            }
            
            // 验证签名
            if (!qrCodeService.verifySignature(userId, timestamp, nonce, signature)) {
                throw new IllegalArgumentException("二维码签名无效");
            }
            
            return userId;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @GetMapping(value = "/api/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
        public byte[] generateQRCode(@RequestParam String userId) {
            return qrCodeService.generateQRCode(userId);
        }
}