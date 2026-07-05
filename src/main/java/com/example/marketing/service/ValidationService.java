package com.example.marketing.service;

import com.example.marketing.dto.PredictionRow;
import com.example.marketing.entity.FeatureDataset;
import com.example.marketing.entity.ValidationRecord;
import com.example.marketing.repository.ValidationRecordRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ValidationService {

    private final ModelTrainingService modelTrainingService;
    private final ValidationRecordRepository validationRepo;

    public ValidationService(ModelTrainingService modelTrainingService,
                             ValidationRecordRepository validationRepo) {
        this.modelTrainingService = modelTrainingService;
        this.validationRepo = validationRepo;
    }

    /**
     * 用多个阈值点计算指标曲线（用于前端 ECharts 展示）
     */
    public Map<String, Object> computeThresholdCurve(List<FeatureDataset> features,
                                                      List<Integer> actualLabels,
                                                      String algorithm) {
        modelTrainingService.trainAndEvaluate();

        double[] thresholds = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9};
        List<Double> thresholdList = new ArrayList<>();
        List<Double> accuracyList = new ArrayList<>();
        List<Double> precisionList = new ArrayList<>();
        List<Double> recallList = new ArrayList<>();
        List<Double> f1List = new ArrayList<>();
        List<Double> maeList = new ArrayList<>();

        for (double t : thresholds) {
            List<PredictionRow> predictions = modelTrainingService.predictWithThreshold(features, algorithm, t);
            Map<String, Double> metrics = computeMetrics(predictions, actualLabels);
            thresholdList.add(t);
            accuracyList.add(metrics.get("accuracy"));
            precisionList.add(metrics.get("precision"));
            recallList.add(metrics.get("recall"));
            f1List.add(metrics.get("f1"));
            maeList.add(metrics.get("mae"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("thresholds", thresholdList);
        result.put("accuracy", accuracyList);
        result.put("precision", precisionList);
        result.put("recall", recallList);
        result.put("f1", f1List);
        result.put("mae", maeList);
        return result;
    }

    /**
     * 用单个阈值计算指标并存入记录
     */
    public Map<String, Double> validateWithThreshold(List<FeatureDataset> features,
                                                      List<Integer> actualLabels,
                                                      String algorithm,
                                                      double threshold,
                                                      Long userId) {
        modelTrainingService.trainAndEvaluate();
        List<PredictionRow> predictions = modelTrainingService.predictWithThreshold(features, algorithm, threshold);
        Map<String, Double> metrics = computeMetrics(predictions, actualLabels);

        ValidationRecord record = ValidationRecord.builder()
                .sysUserId(userId)
                .paramValue(threshold)
                .algorithm(algorithm)
                .accuracy(metrics.get("accuracy"))
                .precisionVal(metrics.get("precision"))
                .recallVal(metrics.get("recall"))
                .f1(metrics.get("f1"))
                .mae(metrics.get("mae"))
                .build();
        validationRepo.save(record);

        return metrics;
    }

    private Map<String, Double> computeMetrics(List<PredictionRow> predictions, List<Integer> actuals) {
        int n = Math.min(predictions.size(), actuals.size());
        int tp = 0, fp = 0, tn = 0, fn = 0;
        double maeSum = 0;

        for (int i = 0; i < n; i++) {
            int pred = predictions.get(i).getPredictedIsWillingPurchase();
            int actual = actuals.get(i);
            if (pred == 1 && actual == 1) tp++;
            else if (pred == 1 && actual == 0) fp++;
            else if (pred == 0 && actual == 0) tn++;
            else fn++;
            maeSum += Math.abs(pred - actual);
        }

        double accuracy = n == 0 ? 0 : (double) (tp + tn) / n;
        double precision = (tp + fp) == 0 ? 0 : (double) tp / (tp + fp);
        double recall = (tp + fn) == 0 ? 0 : (double) tp / (tp + fn);
        double f1 = (precision + recall) == 0 ? 0 : 2 * precision * recall / (precision + recall);
        double mae = n == 0 ? 0 : maeSum / n;

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("accuracy", Math.round(accuracy * 10000) / 100.0);
        metrics.put("precision", Math.round(precision * 10000) / 100.0);
        metrics.put("recall", Math.round(recall * 10000) / 100.0);
        metrics.put("f1", Math.round(f1 * 10000) / 100.0);
        metrics.put("mae", Math.round(mae * 10000) / 10000.0);
        return metrics;
    }
}
