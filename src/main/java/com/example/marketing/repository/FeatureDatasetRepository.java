package com.example.marketing.repository;

import com.example.marketing.entity.FeatureDataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeatureDatasetRepository extends JpaRepository<FeatureDataset, Long> {
    List<FeatureDataset> findTop10ByOrderByIdDesc();
    List<FeatureDataset> findByUserIdIn(List<String> userIds);
}
