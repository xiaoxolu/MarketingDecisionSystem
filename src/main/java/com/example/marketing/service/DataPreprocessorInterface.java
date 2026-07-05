package com.example.marketing.service;

import com.example.marketing.entity.FeatureDataset;
import com.example.marketing.entity.UserBehavior;

import java.util.List;

public interface DataPreprocessorInterface {
    List<UserBehavior> loadRawData(String filePath);

    List<UserBehavior> cleanData(List<UserBehavior> rawList);

    List<FeatureDataset> extractFeatures(List<UserBehavior> behaviors);

    /**
     * 流式处理大文件：边读 CSV 边分批存入数据库，不会撑爆内存。
     * 返回 [行为记录数, 用户特征数]
     */
    long[] streamProcessLargeFile(String filePath);
}
