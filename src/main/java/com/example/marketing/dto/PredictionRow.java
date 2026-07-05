package com.example.marketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionRow {
    private String userId;
    private Double browseDurationAvg;
    private Double cartFrequency;
    private Double purchaseRate;
    private Integer predictedIsWillingPurchase;
    private Double probability;
    private String segment;
    private String strategy;
}
