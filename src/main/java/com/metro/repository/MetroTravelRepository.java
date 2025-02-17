package com.metro.repository;

import com.metro.entity.MetroTravel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetroTravelRepository extends JpaRepository<MetroTravel, Long> {
    MetroTravel findByUserIdAndStatus(String userId, String status);
}