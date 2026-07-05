package com.example.marketing.controller;

import com.example.marketing.entity.FeatureDataset;
import com.example.marketing.entity.SysUser;
import com.example.marketing.repository.FeatureDatasetRepository;
import com.example.marketing.service.UserService;
import com.example.marketing.service.ValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/validate")
public class ValidationController {

    private final ValidationService validationService;
    private final FeatureDatasetRepository featureDatasetRepository;
    private final UserService userService;

    public ValidationController(ValidationService validationService,
                                FeatureDatasetRepository featureDatasetRepository,
                                UserService userService) {
        this.validationService = validationService;
        this.featureDatasetRepository = featureDatasetRepository;
        this.userService = userService;
    }

    @GetMapping("/threshold-curve")
    public ResponseEntity<Map<String, Object>> thresholdCurve(
            @RequestParam(defaultValue = "RandomForest") String algorithm) {

        List<FeatureDataset> features = featureDatasetRepository.findAll();
        if (features.isEmpty()) {
            return ResponseEntity.ok(Collections.singletonMap("error", "暂无数据，请先上传"));
        }
        if (features.size() > 200) {
            features = features.subList(0, 200);
        }

        List<Integer> actualLabels = generateActualLabels(features);
        Map<String, Object> curve = validationService.computeThresholdCurve(features, actualLabels, algorithm);
        return ResponseEntity.ok(curve);
    }

    @PostMapping("/single")
    public ResponseEntity<Map<String, Double>> validateSingle(
            @RequestParam(defaultValue = "0.5") double threshold,
            @RequestParam(defaultValue = "RandomForest") String algorithm,
            HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) userId = 0L;

        List<FeatureDataset> features = featureDatasetRepository.findAll();
        if (features.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyMap());
        }
        if (features.size() > 200) {
            features = features.subList(0, 200);
        }

        List<Integer> actualLabels = generateActualLabels(features);
        Map<String, Double> metrics = validationService.validateWithThreshold(
                features, actualLabels, algorithm, threshold, userId);
        return ResponseEntity.ok(metrics);
    }

    /**
     * 根据用户特征综合生成模拟的"实际购买"标签。
     * 综合 cartFrequency、purchaseRate、browseDurationAvg 来判断，
     * 确保正例和负例都有一定数量，让指标有意义。
     */
    private List<Integer> generateActualLabels(List<FeatureDataset> features) {
        return features.stream().map(f -> {
            double score = 0;
            if (f.getCartFrequency() != null) score += f.getCartFrequency() * 2.0;
            if (f.getPurchaseRate() != null) score += f.getPurchaseRate() * 3.0;
            if (f.getBrowseDurationAvg() != null) score += f.getBrowseDurationAvg() / 200.0;
            if (f.getReviewScoreAvg() != null) score += (f.getReviewScoreAvg() - 3.0) / 5.0;
            // 用 userId 的 hash 做一点随机扰动，让数据更分散
            int hash = f.getUserId() == null ? 0 : f.getUserId().hashCode();
            double noise = (Math.abs(hash) % 100) / 200.0 - 0.25;
            score += noise;
            return score > 0.8 ? 1 : 0;
        }).collect(Collectors.toList());
    }
}
