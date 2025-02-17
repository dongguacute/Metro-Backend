package com.metro.service;

import cn.hutool.extra.qrcode.QrCodeUtil;
import com.metro.entity.MetroTravel;
import com.metro.repository.MetroTravelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

@Service
public class QRCodeService {
    
    @Autowired
    private MetroTravelRepository metroTravelRepository;

    public byte[] generateQRCode(String userId) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            String content = String.format("METRO_USER_%s_%d", userId, System.currentTimeMillis());
            QrCodeUtil.generate(content, 200, 200, "png", outputStream);
            return outputStream.toByteArray();
        } finally {
            try {
                outputStream.close();
            } catch (Exception ignored) {}
        }
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