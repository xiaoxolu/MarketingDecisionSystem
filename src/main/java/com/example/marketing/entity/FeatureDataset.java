package com.example.marketing.entity;

import javax.persistence.*;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "feature_dataset")
public class FeatureDataset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "browse_duration_avg")
    private Double browseDurationAvg;

    @Column(name = "cart_frequency")
    private Double cartFrequency;

    @Column(name = "purchase_rate")
    private Double purchaseRate;

    @Column(name = "review_score_avg")
    private Double reviewScoreAvg;

    @Column(name = "product_hot_level")
    private Integer productHotLevel;

    @Column(name = "product_category")
    private Integer productCategory;

    @Column(name = "product_price_range")
    private Integer productPriceRange;

    @Column(name = "age_group")
    private Integer ageGroup;

    @Column(name = "consume_level")
    private Integer consumeLevel;

    @Column(name = "city_level")
    private Integer cityLevel;

    @Column(name = "is_willing_purchase")
    private Integer isWillingPurchase;
}

