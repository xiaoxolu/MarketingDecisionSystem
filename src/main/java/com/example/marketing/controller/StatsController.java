package com.example.marketing.controller;

import com.example.marketing.service.ModelTrainingService;
import com.example.marketing.service.StatsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final StatsService statsService;
    private final ModelTrainingService modelTrainingService;
    private static final CacheControl NO_CACHE = CacheControl.noStore();

    public StatsController(StatsService statsService, ModelTrainingService modelTrainingService) {
        this.statsService = statsService;
        this.modelTrainingService = modelTrainingService;
    }

    @GetMapping("/stats/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        return ResponseEntity.ok().cacheControl(NO_CACHE).body(statsService.getOverviewStats());
    }

    @GetMapping("/stats/behavior-type")
    public ResponseEntity<List<Map<String, Object>>> behaviorType() {
        return ResponseEntity.ok().cacheControl(NO_CACHE).body(statsService.getBehaviorTypeDistribution());
    }

    @GetMapping("/stats/monthly-trend")
    public ResponseEntity<Map<String, Object>> monthlyTrend() {
        return ResponseEntity.ok().cacheControl(NO_CACHE).body(statsService.getMonthlyTrend());
    }

    @GetMapping("/stats/top-users")
    public ResponseEntity<List<Map<String, Object>>> topUsers() {
        return ResponseEntity.ok().cacheControl(NO_CACHE).body(statsService.getTop10Users());
    }

    @GetMapping("/stats/funnel")
    public ResponseEntity<Map<String, Long>> funnel() {
        return ResponseEntity.ok().cacheControl(NO_CACHE).body(statsService.getConversionFunnel());
    }

    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareModels() {
        modelTrainingService.trainAndEvaluate();
        Map<String, ModelTrainingService.AlgoMetrics> all = modelTrainingService.getAllMetrics();

        List<Map<String, Object>> algorithms = new ArrayList<>();
        Map<String, String> nameMap = new LinkedHashMap<>();
        nameMap.put("RandomForest", "随机森林");
        nameMap.put("Logistic", "逻辑回归");
        nameMap.put("NaiveBayes", "朴素贝叶斯");

        for (Map.Entry<String, ModelTrainingService.AlgoMetrics> entry : all.entrySet()) {
            ModelTrainingService.AlgoMetrics m = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", entry.getKey());
            item.put("name", nameMap.getOrDefault(entry.getKey(), entry.getKey()));
            item.put("accuracy", Math.round(m.accuracy * 10000) / 100.0);
            item.put("precision", Math.round(m.precision * 10000) / 100.0);
            item.put("recall", Math.round(m.recall * 10000) / 100.0);
            item.put("f1", Math.round(m.f1 * 10000) / 100.0);
            item.put("confusionMatrix", m.confusionMatrix);
            algorithms.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithms", algorithms);
        result.put("conclusion", modelTrainingService.compareModels());
        return ResponseEntity.ok(result);
    }
}
