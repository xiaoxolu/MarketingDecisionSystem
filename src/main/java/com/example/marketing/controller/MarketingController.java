package com.example.marketing.controller;

import com.example.marketing.dto.PredictionRow;
import com.example.marketing.entity.FeatureDataset;
import com.example.marketing.entity.SysUser;
import com.example.marketing.entity.UserBehavior;
import com.example.marketing.repository.FeatureDatasetRepository;
import com.example.marketing.repository.UserBehaviorRepository;
import com.example.marketing.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping
public class MarketingController {

    private final DataPreprocessorInterface dataPreprocessor;
    private final UserBehaviorRepository userBehaviorRepository;
    private final FeatureDatasetRepository featureDatasetRepository;
    private final ModelTrainingService modelTrainingService;
    private final MarketingStrategyService marketingStrategyService;
    private final ChartService chartService;
    private final UserService userService;

    public MarketingController(DataPreprocessorInterface dataPreprocessor,
                                UserBehaviorRepository userBehaviorRepository,
                                FeatureDatasetRepository featureDatasetRepository,
                                ModelTrainingService modelTrainingService,
                                MarketingStrategyService marketingStrategyService,
                                ChartService chartService,
                                UserService userService) {
        this.dataPreprocessor = dataPreprocessor;
        this.userBehaviorRepository = userBehaviorRepository;
        this.featureDatasetRepository = featureDatasetRepository;
        this.modelTrainingService = modelTrainingService;
        this.marketingStrategyService = marketingStrategyService;
        this.chartService = chartService;
        this.userService = userService;
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        populateUserInfo(model, session);

        // 从 Session 恢复上次预测结果
        Map<String, Object> cached = (Map<String, Object>) session.getAttribute("lastPrediction");
        if (cached != null) {
            for (Map.Entry<String, Object> e : cached.entrySet()) {
                model.addAttribute(e.getKey(), e.getValue());
            }
            return "index";
        }

        model.addAttribute("predictions", Collections.emptyList());
        model.addAttribute("strategyText", "请先上传数据后开始预测。");
        return "index";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "clearOld", defaultValue = "true") boolean clearOld,
                         RedirectAttributes ra) {
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("uploadMsg", "请选择一个文件！");
            ra.addFlashAttribute("uploadMsgType", "warning");
            return "redirect:/";
        }
        try {
            if (clearOld) {
                featureDatasetRepository.deleteAllInBatch();
                userBehaviorRepository.deleteAllInBatch();
            }

            Path tempDir = Files.createTempDirectory("upload_marketing_");
            String filename = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
            Path tempFile = tempDir.resolve(filename);
            file.transferTo(tempFile);

            long fileSize = Files.size(tempFile);
            long[] counts;
            if (fileSize > 100 * 1024 * 1024) {
                counts = dataPreprocessor.streamProcessLargeFile(tempFile.toString());
            } else {
                List<UserBehavior> raw = dataPreprocessor.loadRawData(tempFile.toString());
                List<UserBehavior> cleaned = dataPreprocessor.cleanData(raw);
                userBehaviorRepository.saveAll(cleaned);
                List<FeatureDataset> features = dataPreprocessor.extractFeatures(cleaned);
                featureDatasetRepository.saveAll(features);
                counts = new long[]{cleaned.size(), features.size()};
            }
            Files.deleteIfExists(tempFile);
            ra.addFlashAttribute("uploadMsg",
                    "上传成功！" + (clearOld ? "已清空旧数据。" : "已追加到现有数据。")
                    + "共处理 " + counts[0] + " 条行为记录，提取 " + counts[1] + " 位用户特征。");
            ra.addFlashAttribute("uploadMsgType", "success");
        } catch (Exception e) {
            ra.addFlashAttribute("uploadMsg", "上传失败：" + e.getMessage());
            ra.addFlashAttribute("uploadMsgType", "danger");
        }
        return "redirect:/";
    }

    @PostMapping("/clear-data")
    public String clearData(RedirectAttributes ra, HttpSession session) {
        featureDatasetRepository.deleteAllInBatch();
        userBehaviorRepository.deleteAllInBatch();
        session.removeAttribute("lastPrediction");
        ra.addFlashAttribute("uploadMsg", "已清空所有数据！");
        ra.addFlashAttribute("uploadMsgType", "info");
        return "redirect:/";
    }

    private void cachePredictionToSession(HttpSession session, Model model) {
        Map<String, Object> cache = new LinkedHashMap<>();
        for (String key : new String[]{
                "predictions", "totalUsers", "willingCount", "unwillingCount",
                "highCount", "midCount", "lowCount",
                "strategyText", "predicted", "selectedAlgorithm", "selectedAlgoName",
                "rfAccuracy", "rfPrecision", "rfRecall", "rfF1",
                "confusionMatrix", "compareResult", "algoMetricsList",
                "startDate", "endDate"}) {
            Object val = model.getAttribute(key);
            if (val != null) cache.put(key, val);
        }
        session.setAttribute("lastPrediction", cache);
    }

    @GetMapping("/predict")
    public String predict(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "RandomForest") String algorithm,
            Model model, HttpSession session) {

        populateUserInfo(model, session);
        modelTrainingService.trainAndEvaluate();
        String compareResult = modelTrainingService.compareModels();

        // 获取特征数据：有时间筛选则从行为表实时提取，否则用已有特征表
        List<FeatureDataset> allFeatures;
        if (startDate != null && endDate != null) {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);
            List<UserBehavior> filtered = userBehaviorRepository.findByBehaviorTimeBetween(start, end);
            allFeatures = dataPreprocessor.extractFeatures(filtered);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
        } else {
            allFeatures = featureDatasetRepository.findAll();
        }

        model.addAttribute("selectedAlgorithm", algorithm);

        if (allFeatures == null || allFeatures.isEmpty()) {
            model.addAttribute("predictions", Collections.emptyList());
            model.addAttribute("strategyText", "所选时间范围内暂无数据，请调整筛选条件或先上传数据。");
            model.addAttribute("predicted", true);
            model.addAttribute("totalUsers", 0);
            addModelMetrics(model, compareResult, algorithm);
            cachePredictionToSession(session, model);
            return "index";
        }

        // 限制最多对前 200 个用户做预测（避免超大数据量卡顿）
        List<FeatureDataset> features = allFeatures.size() > 200
                ? allFeatures.subList(0, 200) : allFeatures;

        List<PredictionRow> allRows = modelTrainingService.predictPurchaseIntention(features, algorithm);
        Map<String, String> userSegments = modelTrainingService.performKMeansClustering(features);

        String overallSegment = resolveOverallSegment(userSegments);
        String overallStrategy = marketingStrategyService.getStrategyBySegment(overallSegment);

        long willingCount = 0;
        for (PredictionRow row : allRows) {
            String seg = userSegments.get(row.getUserId());
            row.setSegment(seg);
            row.setStrategy(seg == null ? "暂无策略" : marketingStrategyService.getStrategyBySegment(seg));
            if (row.getPredictedIsWillingPurchase() != null && row.getPredictedIsWillingPurchase() == 1) {
                willingCount++;
            }
        }

        // 前端表格只展示前 20 条，但统计信息基于全量
        List<PredictionRow> displayRows = allRows.size() > 20
                ? allRows.subList(0, 20) : allRows;

        model.addAttribute("predictions", displayRows);
        model.addAttribute("totalUsers", allRows.size());
        model.addAttribute("willingCount", willingCount);
        model.addAttribute("unwillingCount", allRows.size() - willingCount);

        // 分群统计
        long highCount = userSegments.values().stream().filter("高意向用户"::equals).count();
        long midCount = userSegments.values().stream().filter("中意向用户"::equals).count();
        long lowCount = userSegments.values().stream().filter("低意向用户"::equals).count();
        model.addAttribute("highCount", highCount);
        model.addAttribute("midCount", midCount);
        model.addAttribute("lowCount", lowCount);

        model.addAttribute("strategyText", "共分析 " + allRows.size() + " 位用户 | "
                + overallSegment + " 占主导 | 推荐策略：" + overallStrategy);
        model.addAttribute("predicted", true);
        addModelMetrics(model, compareResult, algorithm);

        // 缓存预测结果到 Session，回到首页时可恢复
        cachePredictionToSession(session, model);

        return "index";
    }

    @GetMapping("/stats")
    public String statsPage(Model model, HttpSession session) {
        populateUserInfo(model, session);
        return "stats";
    }

    @GetMapping("/flow")
    public String flowPage(Model model, HttpSession session) {
        populateUserInfo(model, session);
        return "flow";
    }

    @GetMapping("/validate")
    public String validatePage(Model model, HttpSession session) {
        populateUserInfo(model, session);
        int vip = getVipLevel(session);
        if (vip < 2) {
            model.addAttribute("vipRequired", 2);
            model.addAttribute("vipRequiredName", "年度会员");
        }
        return "validate";
    }

    @GetMapping("/export-predictions")
    public ResponseEntity<byte[]> exportPredictions(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "RandomForest") String algorithm,
            HttpSession session) {

        int vip = getVipLevel(session);
        if (vip < 1) return ResponseEntity.status(403).build();

        modelTrainingService.trainAndEvaluate();
        List<FeatureDataset> features;
        if (startDate != null && endDate != null) {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);
            List<UserBehavior> filtered = userBehaviorRepository.findByBehaviorTimeBetween(start, end);
            features = dataPreprocessor.extractFeatures(filtered);
        } else {
            features = featureDatasetRepository.findAll();
        }
        if (features == null || features.isEmpty()) return ResponseEntity.noContent().build();
        if (features.size() > 200) features = features.subList(0, 200);

        List<PredictionRow> rows = modelTrainingService.predictPurchaseIntention(features, algorithm);
        Map<String, String> segs = modelTrainingService.performKMeansClustering(features);
        for (PredictionRow row : rows) {
            String seg = segs.get(row.getUserId());
            row.setSegment(seg);
            row.setStrategy(seg == null ? "" : marketingStrategyService.getStrategyBySegment(seg));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("用户ID,浏览时长均值,加购频率,购买率,预测意愿,概率,意向分群,营销策略\n");
        for (PredictionRow r : rows) {
            sb.append(r.getUserId()).append(",")
              .append(String.format("%.2f", r.getBrowseDurationAvg())).append(",")
              .append(String.format("%.4f", r.getCartFrequency())).append(",")
              .append(String.format("%.4f", r.getPurchaseRate())).append(",")
              .append(r.getPredictedIsWillingPurchase()).append(",")
              .append(String.format("%.4f", r.getProbability())).append(",")
              .append(r.getSegment() == null ? "" : r.getSegment()).append(",")
              .append(r.getStrategy() == null ? "" : r.getStrategy()).append("\n");
        }

        byte[] csv = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set("Content-Disposition", "attachment; filename=predictions.csv");
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    private int getVipLevel(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return 0;
        SysUser user = userService.getById(userId);
        return user == null ? 0 : user.getEffectiveVipLevel();
    }

    private void addModelMetrics(Model model, String compareResult, String algorithm) {
        Map<String, String> nameMap = new LinkedHashMap<>();
        nameMap.put("RandomForest", "随机森林");
        nameMap.put("Logistic", "逻辑回归");
        nameMap.put("NaiveBayes", "朴素贝叶斯");

        ModelTrainingService.AlgoMetrics cur = modelTrainingService.getMetrics(algorithm);
        model.addAttribute("selectedAlgoName", nameMap.getOrDefault(algorithm, algorithm));
        model.addAttribute("rfAccuracy", String.format("%.2f%%", cur.accuracy * 100));
        model.addAttribute("rfPrecision", String.format("%.2f%%", cur.precision * 100));
        model.addAttribute("rfRecall", String.format("%.2f%%", cur.recall * 100));
        model.addAttribute("rfF1", String.format("%.4f", cur.f1));
        model.addAttribute("confusionMatrix", cur.confusionMatrix);
        model.addAttribute("compareResult", compareResult);

        // 传递三个算法的完整指标供前端表格对比
        Map<String, ModelTrainingService.AlgoMetrics> all = modelTrainingService.getAllMetrics();
        List<Map<String, Object>> algoList = new ArrayList<>();
        for (Map.Entry<String, ModelTrainingService.AlgoMetrics> entry : all.entrySet()) {
            ModelTrainingService.AlgoMetrics m = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", entry.getKey());
            item.put("name", nameMap.getOrDefault(entry.getKey(), entry.getKey()));
            item.put("accuracy", String.format("%.2f", m.accuracy * 100));
            item.put("precision", String.format("%.2f", m.precision * 100));
            item.put("recall", String.format("%.2f", m.recall * 100));
            item.put("f1", String.format("%.4f", m.f1));
            item.put("selected", entry.getKey().equals(algorithm));
            algoList.add(item);
        }
        model.addAttribute("algoMetricsList", algoList);
    }

    private String resolveOverallSegment(Map<String, String> userSegments) {
        if (userSegments == null || userSegments.isEmpty()) return "低意向用户";
        if (userSegments.containsValue("高意向用户")) return "高意向用户";
        if (userSegments.containsValue("中意向用户")) return "中意向用户";
        return "低意向用户";
    }

    private void populateUserInfo(Model model, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId != null) {
            SysUser user = userService.getById(userId);
            if (user != null) {
                model.addAttribute("currentUser", user);
                model.addAttribute("vipLevel", user.getEffectiveVipLevel());
            }
        }
    }

    @GetMapping("/accuracy-chart")
    public ResponseEntity<byte[]> chart() {
        byte[] png = chartService.generateAccuracyChart();
        if (png.length == 0) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return ResponseEntity.ok().headers(headers).body(png);
    }

    @GetMapping("/f1-chart")
    public ResponseEntity<byte[]> f1Chart() {
        byte[] png = chartService.generateF1ComparisonChart();
        if (png.length == 0) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return ResponseEntity.ok().headers(headers).body(png);
    }
}
