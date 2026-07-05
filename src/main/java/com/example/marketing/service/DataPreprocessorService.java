package com.example.marketing.service;

import com.example.marketing.entity.FeatureDataset;
import com.example.marketing.entity.UserBehavior;
import com.example.marketing.repository.FeatureDatasetRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataPreprocessorService implements DataPreprocessorInterface {

    private static final Logger log = LoggerFactory.getLogger(DataPreprocessorService.class);

    private static final int BATCH_SIZE = 5000;

    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    private static final Map<String, String> BEHAVIOR_TYPE_MAP = new HashMap<>();
    static {
        BEHAVIOR_TYPE_MAP.put("pv", "浏览");
        BEHAVIOR_TYPE_MAP.put("cart", "加购");
        BEHAVIOR_TYPE_MAP.put("buy", "下单");
        BEHAVIOR_TYPE_MAP.put("fav", "评价");
    }

    private enum CsvFormat { STANDARD, TAOBAO }

    @PersistenceContext
    private EntityManager entityManager;

    private final FeatureDatasetRepository featureDatasetRepository;

    public DataPreprocessorService(FeatureDatasetRepository featureDatasetRepository) {
        this.featureDatasetRepository = featureDatasetRepository;
    }

    // ============ 公开接口方法 ============

    @Override
    public List<UserBehavior> loadRawData(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) return Collections.emptyList();

        CsvFormat format = detectFormat(path);
        log.info("检测到 CSV 格式：{}", format);

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = buildParser(reader, format)) {

            List<UserBehavior> list = new ArrayList<>();
            for (CSVRecord r : parser) {
                UserBehavior ub = parseRecord(r, format);
                if (ub != null) list.add(ub);
            }
            return list;
        } catch (IOException e) {
            log.error("读取文件失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<UserBehavior> cleanData(List<UserBehavior> rawList) {
        if (rawList == null || rawList.isEmpty()) return Collections.emptyList();

        for (UserBehavior b : rawList) {
            if (b.getPageDuration() == null) b.setPageDuration(0);
        }

        Map<String, UserBehavior> dedup = new LinkedHashMap<>();
        for (UserBehavior b : rawList) {
            if (b.getUserId() == null || b.getProductId() == null
                    || b.getBehaviorType() == null || b.getBehaviorTime() == null) {
                continue;
            }
            String key = b.getUserId() + "|" + b.getProductId() + "|"
                    + b.getBehaviorType() + "|" + b.getBehaviorTime();
            dedup.putIfAbsent(key, b);
        }
        return new ArrayList<>(dedup.values());
    }

    @Override
    public List<FeatureDataset> extractFeatures(List<UserBehavior> behaviors) {
        if (behaviors == null || behaviors.isEmpty()) return Collections.emptyList();

        Map<String, List<UserBehavior>> byUser = behaviors.stream()
                .filter(b -> b.getUserId() != null)
                .collect(Collectors.groupingBy(UserBehavior::getUserId));

        List<FeatureDataset> result = new ArrayList<>(byUser.size());
        for (Map.Entry<String, List<UserBehavior>> entry : byUser.entrySet()) {
            result.add(buildFeatureForUser(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @Override
    @Transactional
    public long[] streamProcessLargeFile(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) return new long[]{0, 0};

        CsvFormat format = detectFormat(path);
        log.info("大文件流式处理，检测到格式：{}", format);

        long totalSaved = 0;
        Set<String> dedupKeys = new HashSet<>();
        Map<String, UserStats> statsMap = new LinkedHashMap<>();

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = buildParser(reader, format)) {

            List<UserBehavior> batch = new ArrayList<>(BATCH_SIZE);

            for (CSVRecord r : parser) {
                UserBehavior ub = parseRecord(r, format);
                if (ub == null) continue;
                if (ub.getPageDuration() == null) ub.setPageDuration(0);

                String dedupKey = ub.getUserId() + "|" + ub.getProductId() + "|"
                        + ub.getBehaviorType() + "|" + ub.getBehaviorTime();
                if (!dedupKeys.add(dedupKey)) continue;

                if (dedupKeys.size() > 5_000_000) {
                    dedupKeys.clear();
                }

                accumulateStats(statsMap, ub);

                batch.add(ub);
                if (batch.size() >= BATCH_SIZE) {
                    totalSaved += flushBatch(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                totalSaved += flushBatch(batch);
                batch.clear();
            }

        } catch (IOException e) {
            log.error("流式处理文件失败: {}", e.getMessage(), e);
            return new long[]{totalSaved, 0};
        }

        dedupKeys.clear();

        long featureCount = saveFeaturesByStats(statsMap);
        statsMap.clear();

        log.info("流式处理完成：行为记录 {} 条，用户特征 {} 条", totalSaved, featureCount);
        return new long[]{totalSaved, featureCount};
    }

    // ============ 格式检测 ============

    /**
     * 读取文件第一行，判断是标准格式还是淘宝 UserBehavior 数据集格式。
     * 淘宝格式：无表头或首行为数据，列为 user_id,item_id,category_id,behavior_type(pv/cart/buy/fav),timestamp
     * 标准格式：首行包含 user_id, product_id, behavior_type 等表头
     */
    private CsvFormat detectFormat(Path path) {
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String firstLine = br.readLine();
            if (firstLine == null) return CsvFormat.STANDARD;

            String lower = firstLine.toLowerCase().trim();

            // 如果第一行包含已知表头关键词，就是标准格式
            if (lower.contains("user_id") && lower.contains("behavior_time")) {
                return CsvFormat.STANDARD;
            }

            // 尝试按逗号拆分，看看是不是 5 列、且第 4 列是 pv/cart/buy/fav
            String[] parts = firstLine.split(",");
            if (parts.length >= 5) {
                String col3 = parts[3].trim().toLowerCase();
                if (BEHAVIOR_TYPE_MAP.containsKey(col3)) {
                    return CsvFormat.TAOBAO;
                }
                // 也可能第一行是不同的表头名（如 UserID,ItemID 等），第二行才是数据
                String secondLine = br.readLine();
                if (secondLine != null) {
                    String[] parts2 = secondLine.split(",");
                    if (parts2.length >= 5) {
                        String col3v2 = parts2[3].trim().toLowerCase();
                        if (BEHAVIOR_TYPE_MAP.containsKey(col3v2)) {
                            return CsvFormat.TAOBAO;
                        }
                    }
                }
            }

            return CsvFormat.STANDARD;
        } catch (IOException e) {
            return CsvFormat.STANDARD;
        }
    }

    private CSVParser buildParser(Reader reader, CsvFormat format) throws IOException {
        if (format == CsvFormat.TAOBAO) {
            // 淘宝格式：无表头，按位置解析
            return CSVFormat.DEFAULT
                    .builder()
                    .setTrim(true)
                    .build()
                    .parse(reader);
        } else {
            // 标准格式：第一行是表头
            return CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);
        }
    }

    // ============ 解析单行 ============

    private UserBehavior parseRecord(CSVRecord r, CsvFormat format) {
        if (format == CsvFormat.TAOBAO) {
            return parseTaobaoRecord(r);
        } else {
            return parseStandardRecord(r);
        }
    }

    /**
     * 淘宝数据集格式（按位置）：
     * 列0=user_id, 列1=item_id, 列2=category_id, 列3=behavior_type(pv/cart/buy/fav), 列4=timestamp(Unix秒)
     */
    private UserBehavior parseTaobaoRecord(CSVRecord r) {
        if (r.size() < 5) return null;

        String userId = emptyToNull(r.get(0));
        String productId = emptyToNull(r.get(1));
        // 列2 = category_id，暂不直接使用
        String behaviorTypeRaw = emptyToNull(r.get(3));
        String timestampStr = emptyToNull(r.get(4));

        if (userId == null || productId == null || behaviorTypeRaw == null || timestampStr == null) {
            return null;
        }

        // 跳过看起来像表头的行
        String lower = behaviorTypeRaw.toLowerCase();
        if (!BEHAVIOR_TYPE_MAP.containsKey(lower)) {
            return null;
        }
        String behaviorType = BEHAVIOR_TYPE_MAP.get(lower);

        LocalDateTime behaviorTime = parseUnixTimestamp(timestampStr);
        if (behaviorTime == null) return null;

        // 淘宝数据没有 page_duration，用随机模拟值（浏览行为 30~180 秒）
        Integer pageDuration = 0;
        if ("浏览".equals(behaviorType)) {
            pageDuration = 30 + Math.abs(userId.hashCode() % 150);
        }

        return UserBehavior.builder()
                .userId(userId)
                .productId(productId)
                .behaviorType(behaviorType)
                .behaviorTime(behaviorTime)
                .pageDuration(pageDuration)
                .build();
    }

    /**
     * 标准格式（按列名）：user_id, product_id, behavior_type, behavior_time, page_duration
     */
    private UserBehavior parseStandardRecord(CSVRecord r) {
        String userId = emptyToNull(r.get("user_id"));
        String productId = emptyToNull(r.get("product_id"));
        String behaviorType = emptyToNull(r.get("behavior_type"));
        String behaviorTimeStr = emptyToNull(r.get("behavior_time"));

        if (userId == null || productId == null || behaviorType == null || behaviorTimeStr == null) {
            return null;
        }

        LocalDateTime behaviorTime = parseDateTime(behaviorTimeStr);
        if (behaviorTime == null) return null;

        String pageDurationStr = safeGet(r, "page_duration");
        Integer pageDuration = null;
        if (pageDurationStr != null && !pageDurationStr.trim().isEmpty()) {
            try {
                pageDuration = Integer.parseInt(pageDurationStr.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        return UserBehavior.builder()
                .userId(userId)
                .productId(productId)
                .behaviorType(behaviorType)
                .behaviorTime(behaviorTime)
                .pageDuration(pageDuration)
                .build();
    }

    // ============ 统计 & 批量写入 ============

    private static class UserStats {
        long totalVisits = 0;
        long cartCount = 0;
        long orderCount = 0;
        long reviewCount = 0;
        long browseCount = 0;
        long browseDurationSum = 0;
    }

    private void accumulateStats(Map<String, UserStats> statsMap, UserBehavior ub) {
        UserStats s = statsMap.computeIfAbsent(ub.getUserId(), k -> new UserStats());
        s.totalVisits++;
        String type = ub.getBehaviorType();
        if ("加购".equals(type)) s.cartCount++;
        else if ("下单".equals(type)) s.orderCount++;
        else if ("评价".equals(type)) s.reviewCount++;
        else if ("浏览".equals(type)) {
            s.browseCount++;
            s.browseDurationSum += (ub.getPageDuration() == null ? 0 : ub.getPageDuration());
        }
    }

    private int flushBatch(List<UserBehavior> batch) {
        for (UserBehavior ub : batch) {
            entityManager.persist(ub);
        }
        entityManager.flush();
        entityManager.clear();
        log.info("已写入一批 {} 条行为记录", batch.size());
        return batch.size();
    }

    private long saveFeaturesByStats(Map<String, UserStats> statsMap) {
        List<FeatureDataset> featureBatch = new ArrayList<>(BATCH_SIZE);
        long count = 0;

        for (Map.Entry<String, UserStats> entry : statsMap.entrySet()) {
            String userId = entry.getKey();
            UserStats s = entry.getValue();

            double cartFrequency = s.totalVisits == 0 ? 0.0 : (double) s.cartCount / s.totalVisits;
            double purchaseRate = s.totalVisits == 0 ? 0.0 : (double) s.orderCount / s.totalVisits;
            double browseDurationAvg = s.browseCount == 0 ? 0.0 : (double) s.browseDurationSum / s.browseCount;
            double reviewScoreAvg = s.reviewCount == 0 ? 3.0 :
                    Math.min(5.0, 2.5 + 2.5 * ((double) s.reviewCount / s.totalVisits));

            int productCategory = Math.abs(userId.hashCode() % 5) + 1;
            int productPriceRange = purchaseRate > 0.5 ? 4 : (purchaseRate > 0.2 ? 3 : 2);
            int ageGroup = Math.abs(userId.hashCode() % 4) + 1;
            int consumeLevel = cartFrequency > 0.5 ? 4 : (cartFrequency > 0.2 ? 3 : 2);
            int cityLevel = Math.abs((userId.hashCode() >> 4) % 3) + 1;

            featureBatch.add(FeatureDataset.builder()
                    .userId(userId)
                    .browseDurationAvg(browseDurationAvg)
                    .cartFrequency(cartFrequency)
                    .purchaseRate(purchaseRate)
                    .reviewScoreAvg(reviewScoreAvg)
                    .productHotLevel(3)
                    .productCategory(productCategory)
                    .productPriceRange(productPriceRange)
                    .ageGroup(ageGroup)
                    .consumeLevel(consumeLevel)
                    .cityLevel(cityLevel)
                    .build());

            if (featureBatch.size() >= BATCH_SIZE) {
                count += featureBatch.size();
                featureDatasetRepository.saveAll(featureBatch);
                entityManager.flush();
                entityManager.clear();
                featureBatch.clear();
            }
        }

        if (!featureBatch.isEmpty()) {
            count += featureBatch.size();
            featureDatasetRepository.saveAll(featureBatch);
            entityManager.flush();
            entityManager.clear();
            featureBatch.clear();
        }

        return count;
    }

    private FeatureDataset buildFeatureForUser(String userId, List<UserBehavior> list) {
        long totalVisits = list.size();
        long cartCount = list.stream().filter(b -> "加购".equals(b.getBehaviorType())).count();
        long orderCount = list.stream().filter(b -> "下单".equals(b.getBehaviorType())).count();
        long reviewCount = list.stream().filter(b -> "评价".equals(b.getBehaviorType())).count();

        double cartFrequency = totalVisits == 0 ? 0.0 : (double) cartCount / totalVisits;
        double purchaseRate = totalVisits == 0 ? 0.0 : (double) orderCount / totalVisits;

        List<Integer> browseDurations = list.stream()
                .filter(b -> "浏览".equals(b.getBehaviorType()))
                .map(UserBehavior::getPageDuration)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        double browseDurationAvg = browseDurations.isEmpty() ? 0.0 :
                browseDurations.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double reviewScoreAvg = reviewCount == 0 ? 3.0 :
                Math.min(5.0, 2.5 + 2.5 * ((double) reviewCount / totalVisits));

        int productCategory = Math.abs(userId.hashCode() % 5) + 1;
        int productPriceRange = purchaseRate > 0.5 ? 4 : (purchaseRate > 0.2 ? 3 : 2);
        int ageGroup = Math.abs(userId.hashCode() % 4) + 1;
        int consumeLevel = cartFrequency > 0.5 ? 4 : (cartFrequency > 0.2 ? 3 : 2);
        int cityLevel = Math.abs((userId.hashCode() >> 4) % 3) + 1;

        return FeatureDataset.builder()
                .userId(userId)
                .browseDurationAvg(browseDurationAvg)
                .cartFrequency(cartFrequency)
                .purchaseRate(purchaseRate)
                .reviewScoreAvg(reviewScoreAvg)
                .productHotLevel(3)
                .productCategory(productCategory)
                .productPriceRange(productPriceRange)
                .ageGroup(ageGroup)
                .consumeLevel(consumeLevel)
                .cityLevel(cityLevel)
                .build();
    }

    // ============ 工具方法 ============

    private static LocalDateTime parseUnixTimestamp(String s) {
        try {
            long ts = Long.parseLong(s.trim());
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneId.of("Asia/Shanghai"));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String s) {
        for (DateTimeFormatter f : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(s.trim(), f);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safeGet(CSVRecord r, String header) {
        try {
            return r.isMapped(header) ? r.get(header) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
