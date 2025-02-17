package com.metro.service;
import java.util.UUID;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import cn.hutool.extra.qrcode.QrCodeUtil;
import com.metro.entity.MetroTravel;
import com.metro.repository.MetroTravelRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Map; // 確保已導入 Map 介面

@Service
public class QRCodeService {
    
    @Autowired
    private MetroTravelRepository metroTravelRepository;
    private static final String SECRET_KEY = "Metro_Secret_Key_2023";
    private static final int MAX_ATTEMPTS = 3;
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> usedSignatures = new ConcurrentHashMap<>();

    public byte[] generateQRCode(String userId) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            long timestamp = System.currentTimeMillis();
            String nonce = generateNonce();
            String signature = generateSignature(userId, timestamp, nonce);
            String content = String.format("METRO_USER_%s_%d_%s_%s", userId, timestamp, nonce, signature);
            QrCodeUtil.generate(content, 200, 200, "png", outputStream);
            return outputStream.toByteArray();
        } finally {
            try {
                outputStream.close();
            } catch (Exception ignored) {}
        }
    }

    private String generateNonce() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateSignature(String userId, long timestamp, String nonce) {
        String data = userId + timestamp + nonce + SECRET_KEY;
        return DigestUtils.sha256Hex(data);
    }

    public boolean verifySignature(String userId, long timestamp, String nonce, String signature) {
        // 检查失败次数
        if (isUserBlocked(userId)) {
            throw new IllegalStateException("账户已被临时锁定，请15分钟后重试");
        }

        // 检查签名是否被使用过
        if (isSignatureUsed(userId, signature)) {
            incrementFailedAttempts(userId);
            throw new IllegalArgumentException("二维码已被使用");
        }

        String expectedSignature = generateSignature(userId, timestamp, nonce);
        if (!expectedSignature.equals(signature)) {
            incrementFailedAttempts(userId);
            return false;
        }

        // 记录已使用的签名
        recordUsedSignature(userId, signature);
        resetFailedAttempts(userId);
        return true;
    }

    private boolean isUserBlocked(String userId) {
        Integer attempts = failedAttempts.get(userId);
        return attempts != null && attempts >= MAX_ATTEMPTS;
    }

    private synchronized void incrementFailedAttempts(String userId) {
        failedAttempts.compute(userId, (key, attempts) -> attempts == null ? 1 : attempts + 1);
    }

    private void resetFailedAttempts(String userId) {
        failedAttempts.remove(userId);
    }

    private boolean isSignatureUsed(String userId, String signature) {
        return usedSignatures.computeIfAbsent(userId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .contains(signature);
    }

    private void recordUsedSignature(String userId, String signature) {
        usedSignatures.computeIfAbsent(userId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(signature);
    }

    @Transactional
    public MetroTravel processStation(String userId, String station) {
        if (userId == null || station == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        MetroTravel travel = metroTravelRepository.findByUserIdAndStatus(userId, "STARTED");
        
        if (travel == null) {
            return createNewTravel(userId, station);
        }
        
        return updateExistingTravel(travel, station);
    }

    private MetroTravel createNewTravel(String userId, String station) {
        MetroTravel travel = new MetroTravel();
        travel.setUserId(userId);
        travel.setStartStation(station);
        travel.setStartTime(LocalDateTime.now());
        travel.setStatus("STARTED");
        return metroTravelRepository.save(travel);
    }

    private MetroTravel updateExistingTravel(MetroTravel travel, String station) {
        // 移除同站限制检查
        travel.setEndStation(station);
        travel.setEndTime(LocalDateTime.now());
        travel.setStatus("COMPLETED");
        return metroTravelRepository.save(travel);
    }
}