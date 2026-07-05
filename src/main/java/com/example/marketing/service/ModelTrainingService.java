package com.example.marketing.service;

import com.example.marketing.dto.PredictionRow;
import com.example.marketing.entity.FeatureDataset;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.Logistic;
import weka.classifiers.trees.RandomForest;
import weka.clusterers.SimpleKMeans;
import weka.core.*;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ModelTrainingService {

    private static final int SIMULATED_DATA_SIZE = 82000;
    private static final int NUM_TREES = 100;
    private static final double TRAIN_RATIO = 0.7;
    private static final int POS_CLASS_INDEX = 1;
    private static final int K = 3;
    private static final long SEED = 42L;

    private final Object lock = new Object();
    private volatile boolean initialized = false;

    private RandomForest randomForestModel;
    private Logistic logisticModel;
    private NaiveBayes naiveBayesModel;
    private Instances cachedTrainData;

    public static class AlgoMetrics {
        public double accuracy;
        public double precision;
        public double recall;
        public double f1;
        public String confusionMatrix;
    }

    private final Map<String, AlgoMetrics> metricsMap = new LinkedHashMap<>();

    private void ensureInitialized() {
        if (initialized) return;
        synchronized (lock) {
            if (initialized) return;

            Instances data = buildSimulatedTrainingInstances(SIMULATED_DATA_SIZE, SEED);
            Random rng = new Random(SEED);
            data.randomize(rng);
            int trainSize = (int) Math.round(data.numInstances() * TRAIN_RATIO);
            Instances train = new Instances(data, 0, trainSize);
            Instances test = new Instances(data, trainSize, data.numInstances() - trainSize);

            try {
                RandomForest rf = new RandomForest();
                rf.setSeed((int) SEED);
                rf.setNumIterations(NUM_TREES);
                rf.buildClassifier(train);
                metricsMap.put("RandomForest", evaluate(rf, train, test));
                randomForestModel = rf;

                Logistic lr = new Logistic();
                lr.buildClassifier(train);
                metricsMap.put("Logistic", evaluate(lr, train, test));
                logisticModel = lr;

                NaiveBayes nb = new NaiveBayes();
                nb.buildClassifier(train);
                metricsMap.put("NaiveBayes", evaluate(nb, train, test));
                naiveBayesModel = nb;

                cachedTrainData = train;
                initialized = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private AlgoMetrics evaluate(AbstractClassifier clf, Instances train, Instances test) throws Exception {
        Evaluation eval = new Evaluation(train);
        eval.evaluateModel(clf, test);
        AlgoMetrics m = new AlgoMetrics();
        m.accuracy = eval.pctCorrect() / 100.0;
        m.precision = eval.precision(POS_CLASS_INDEX);
        m.recall = eval.recall(POS_CLASS_INDEX);
        m.f1 = eval.fMeasure(POS_CLASS_INDEX);
        m.confusionMatrix = eval.toMatrixString();
        return m;
    }

    // ========== 公共 getter ==========

    public void trainAndEvaluate() { ensureInitialized(); }

    public Map<String, AlgoMetrics> getAllMetrics() {
        ensureInitialized();
        return metricsMap;
    }

    public AlgoMetrics getMetrics(String algo) {
        ensureInitialized();
        return metricsMap.getOrDefault(algo, metricsMap.get("RandomForest"));
    }

    public double getAccuracy() { return getMetrics("RandomForest").accuracy; }
    public double getRecall() { return getMetrics("RandomForest").recall; }
    public double getRfF1() { return getMetrics("RandomForest").f1; }
    public double getLogisticF1() { return getMetrics("Logistic").f1; }
    public String getConfusionMatrix() { return getMetrics("RandomForest").confusionMatrix; }

    public String compareModels() {
        ensureInitialized();
        AlgoMetrics rf = metricsMap.get("RandomForest");
        AlgoMetrics lr = metricsMap.get("Logistic");
        AlgoMetrics nb = metricsMap.get("NaiveBayes");

        String best = "RandomForest";
        double bestF1 = rf.f1;
        if (lr.f1 > bestF1) { best = "Logistic"; bestF1 = lr.f1; }
        if (nb.f1 > bestF1) { best = "NaiveBayes"; }

        Map<String, String> nameMap = new LinkedHashMap<>();
        nameMap.put("RandomForest", "随机森林");
        nameMap.put("Logistic", "逻辑回归");
        nameMap.put("NaiveBayes", "朴素贝叶斯");

        return nameMap.get(best) + "模型F1值最高，综合效果最优";
    }

    // ========== KMeans ==========

    public Map<String, String> performKMeansClustering(List<FeatureDataset> userFeatures) {
        if (userFeatures == null || userFeatures.isEmpty()) return new LinkedHashMap<>();
        try {
            ArrayList<Attribute> attrs = new ArrayList<>();
            attrs.add(new Attribute("browse_duration_avg"));
            attrs.add(new Attribute("cart_frequency"));
            attrs.add(new Attribute("purchase_rate"));
            attrs.add(new Attribute("product_hot_level"));

            Instances clusteringData = new Instances("kmeans_data", attrs, userFeatures.size());
            for (FeatureDataset fd : userFeatures) {
                clusteringData.add(new DenseInstance(1.0, new double[]{
                        safeDouble(fd.getBrowseDurationAvg()),
                        safeDouble(fd.getCartFrequency()),
                        safeDouble(fd.getPurchaseRate()),
                        safeIntAsDouble(fd.getProductHotLevel())
                }));
            }

            SimpleKMeans kMeans = new SimpleKMeans();
            kMeans.setNumClusters(K);
            kMeans.setSeed((int) SEED);
            kMeans.buildClusterer(clusteringData);

            Instances centers = kMeans.getClusterCentroids();
            double[] purchaseCenters = new double[K];
            for (int i = 0; i < K; i++) purchaseCenters[i] = centers.instance(i).value(2);

            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < K; i++) idx.add(i);
            idx.sort(Comparator.comparingDouble((Integer i) -> purchaseCenters[i]).reversed());

            int highCluster = idx.get(0), midCluster = idx.get(1);

            Map<String, String> result = new LinkedHashMap<>();
            for (int i = 0; i < userFeatures.size(); i++) {
                int clusterId = kMeans.clusterInstance(clusteringData.instance(i));
                String segment;
                if (clusterId == highCluster) segment = "高意向用户";
                else if (clusterId == midCluster) segment = "中意向用户";
                else segment = "低意向用户";
                result.put(userFeatures.get(i).getUserId(), segment);
            }
            return result;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    // ========== 预测 ==========

    public List<PredictionRow> predictPurchaseIntention(List<FeatureDataset> userFeatures, String algorithm) {
        if (userFeatures == null || userFeatures.isEmpty()) return new ArrayList<>();
        ensureInitialized();

        AbstractClassifier model = getModel(algorithm);
        Instances predData = buildPredictionInstances(userFeatures);

        List<PredictionRow> rows = new ArrayList<>(userFeatures.size());
        for (int i = 0; i < userFeatures.size(); i++) {
            FeatureDataset fd = userFeatures.get(i);
            Instance inst = predData.instance(i);
            int pred = 0;
            double prob = 0.0;
            try {
                double[] dist = model.distributionForInstance(inst);
                prob = dist.length > 1 ? dist[1] : 0.0;
                pred = prob >= 0.5 ? 1 : 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
            rows.add(PredictionRow.builder()
                    .userId(fd.getUserId())
                    .browseDurationAvg(fd.getBrowseDurationAvg())
                    .cartFrequency(fd.getCartFrequency())
                    .purchaseRate(fd.getPurchaseRate())
                    .predictedIsWillingPurchase(pred)
                    .probability(prob)
                    .build());
        }
        return rows;
    }

    public List<PredictionRow> predictPurchaseIntention(List<FeatureDataset> userFeatures) {
        return predictPurchaseIntention(userFeatures, "RandomForest");
    }

    /**
     * 用指定阈值重新计算预测标签（用于验证模块参数调优）
     */
    public List<PredictionRow> predictWithThreshold(List<FeatureDataset> userFeatures, String algorithm, double threshold) {
        if (userFeatures == null || userFeatures.isEmpty()) return new ArrayList<>();
        ensureInitialized();

        AbstractClassifier model = getModel(algorithm);
        Instances predData = buildPredictionInstances(userFeatures);

        List<PredictionRow> rows = new ArrayList<>(userFeatures.size());
        for (int i = 0; i < userFeatures.size(); i++) {
            FeatureDataset fd = userFeatures.get(i);
            Instance inst = predData.instance(i);
            double prob = 0.0;
            try {
                double[] dist = model.distributionForInstance(inst);
                prob = dist.length > 1 ? dist[1] : 0.0;
            } catch (Exception ignored) {}

            rows.add(PredictionRow.builder()
                    .userId(fd.getUserId())
                    .browseDurationAvg(fd.getBrowseDurationAvg())
                    .cartFrequency(fd.getCartFrequency())
                    .purchaseRate(fd.getPurchaseRate())
                    .predictedIsWillingPurchase(prob >= threshold ? 1 : 0)
                    .probability(prob)
                    .build());
        }
        return rows;
    }

    private AbstractClassifier getModel(String algorithm) {
        if ("Logistic".equalsIgnoreCase(algorithm)) return logisticModel;
        if ("NaiveBayes".equalsIgnoreCase(algorithm)) return naiveBayesModel;
        return randomForestModel;
    }

    private Instances buildPredictionInstances(List<FeatureDataset> userFeatures) {
        ArrayList<Attribute> attrs = createTrainingAttributes();
        int classIndex = attrs.size() - 1;
        Instances predData = new Instances("pred_data", attrs, userFeatures.size());
        predData.setClassIndex(classIndex);

        for (FeatureDataset fd : userFeatures) {
            double[] values = new double[attrs.size()];
            values[0] = parseUserIdNumeric(fd.getUserId());
            values[1] = safeDouble(fd.getBrowseDurationAvg());
            values[2] = safeDouble(fd.getCartFrequency());
            values[3] = safeDouble(fd.getPurchaseRate());
            values[4] = safeDouble(fd.getReviewScoreAvg());
            values[5] = safeIntAsDouble(fd.getProductHotLevel());
            values[6] = safeIntAsDouble(fd.getProductCategory());
            values[7] = safeIntAsDouble(fd.getProductPriceRange());
            values[8] = safeIntAsDouble(fd.getAgeGroup());
            values[9] = safeIntAsDouble(fd.getConsumeLevel());
            values[10] = safeIntAsDouble(fd.getCityLevel());
            values[classIndex] = Utils.missingValue();
            DenseInstance inst = new DenseInstance(1.0, values);
            inst.setDataset(predData);
            predData.add(inst);
        }
        return predData;
    }

    // ========== 数据生成 ==========

    private Instances buildSimulatedTrainingInstances(int n, long seed) {
        ArrayList<Attribute> attrs = createTrainingAttributes();
        Instances data = new Instances("simulated_data", attrs, n);
        data.setClassIndex(attrs.size() - 1);
        Random random = new Random(seed);

        for (int i = 0; i < n; i++) {
            double browseDurationAvg = clamp(random.nextGaussian() * 20.0 + 60.0, 0.0, 300.0);
            double cartFrequency = clamp(random.nextDouble(), 0.0, 1.0);
            double purchaseRate = clamp(cartFrequency * 0.75 + random.nextGaussian() * 0.07, 0.0, 1.0);
            double reviewScoreAvg = clamp(random.nextDouble() * 4.0 + 1.0, 1.0, 5.0);
            int productHotLevel = random.nextInt(5) + 1;
            int productCategory = random.nextInt(5) + 1;
            int productPriceRange = random.nextInt(5) + 1;
            int ageGroup = random.nextInt(5) + 1;
            int consumeLevel = random.nextInt(5) + 1;
            int cityLevel = random.nextInt(5) + 1;

            double score = 2.4 * cartFrequency + 3.8 * purchaseRate +
                    0.015 * browseDurationAvg + 0.25 * (reviewScoreAvg / 5.0) +
                    0.18 * productHotLevel - 0.10 * (consumeLevel / 5.0) +
                    0.05 * (cityLevel / 5.0) + 0.02 * (productCategory / 5.0);
            double p = 1.0 / (1.0 + Math.exp(-(score - 2.6)));
            boolean y = random.nextDouble() < p;
            if (random.nextDouble() < 0.16) y = !y;

            double[] values = new double[]{
                    i + 1, browseDurationAvg, cartFrequency, purchaseRate, reviewScoreAvg,
                    productHotLevel, productCategory, productPriceRange, ageGroup,
                    consumeLevel, cityLevel, y ? 1.0 : 0.0
            };
            data.add(new DenseInstance(1.0, values));
        }
        return data;
    }

    private double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    private ArrayList<Attribute> createTrainingAttributes() {
        ArrayList<Attribute> attrs = new ArrayList<>();
        attrs.add(new Attribute("user_id_numeric"));
        attrs.add(new Attribute("browse_duration_avg"));
        attrs.add(new Attribute("cart_frequency"));
        attrs.add(new Attribute("purchase_rate"));
        attrs.add(new Attribute("review_score_avg"));
        attrs.add(new Attribute("product_hot_level"));
        attrs.add(new Attribute("product_category"));
        attrs.add(new Attribute("product_price_range"));
        attrs.add(new Attribute("age_group"));
        attrs.add(new Attribute("consume_level"));
        attrs.add(new Attribute("city_level"));
        ArrayList<String> classVals = new ArrayList<>();
        classVals.add("0");
        classVals.add("1");
        attrs.add(new Attribute("is_willing_purchase", classVals));
        return attrs;
    }

    private double safeDouble(Double v) { return v == null ? 0.0 : v; }
    private double safeIntAsDouble(Integer v) { return v == null ? 0.0 : v.doubleValue(); }

    private double parseUserIdNumeric(String userId) {
        if (userId == null) return 0.0;
        String digits = userId.replaceAll("\\D+", "");
        if (digits.isEmpty()) return 0.0;
        try { return Double.parseDouble(digits); } catch (Exception e) { return 0.0; }
    }
}
