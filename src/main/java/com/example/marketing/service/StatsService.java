package com.example.marketing.service;

import com.example.marketing.repository.UserBehaviorRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StatsService {

    private final UserBehaviorRepository behaviorRepo;

    public StatsService(UserBehaviorRepository behaviorRepo) {
        this.behaviorRepo = behaviorRepo;
    }

    public Map<String, Object> getOverviewStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRecords", behaviorRepo.count());
        stats.put("totalUsers", behaviorRepo.countDistinctUsers());
        stats.put("totalProducts", behaviorRepo.countDistinctProducts());
        stats.put("minTime", behaviorRepo.findMinBehaviorTime());
        stats.put("maxTime", behaviorRepo.findMaxBehaviorTime());
        return stats;
    }

    public List<Map<String, Object>> getBehaviorTypeDistribution() {
        List<Object[]> raw = behaviorRepo.countByBehaviorType();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row[0]);
            item.put("value", ((Number) row[1]).longValue());
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> getMonthlyTrend() {
        List<Object[]> raw = behaviorRepo.countByMonth();
        List<String> months = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (Object[] row : raw) {
            months.add(String.valueOf(row[0]));
            counts.add(((Number) row[1]).longValue());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("months", months);
        result.put("counts", counts);
        return result;
    }

    public List<Map<String, Object>> getTop10Users() {
        List<Object[]> raw = behaviorRepo.findTop10ActiveUsers();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", row[0]);
            item.put("count", ((Number) row[1]).longValue());
            result.add(item);
        }
        return result;
    }

    public Map<String, Long> getConversionFunnel() {
        List<Object[]> raw = behaviorRepo.countByBehaviorType();
        Map<String, Long> typeMap = new LinkedHashMap<>();
        for (Object[] row : raw) {
            typeMap.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        Map<String, Long> funnel = new LinkedHashMap<>();
        funnel.put("浏览", typeMap.getOrDefault("浏览", 0L));
        funnel.put("加购", typeMap.getOrDefault("加购", 0L));
        funnel.put("下单", typeMap.getOrDefault("下单", 0L));
        funnel.put("评价", typeMap.getOrDefault("评价", 0L));
        return funnel;
    }
}
