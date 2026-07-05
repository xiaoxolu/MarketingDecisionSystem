package com.example.marketing.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "validation_record")
public class ValidationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sys_user_id", nullable = false)
    private Long sysUserId;

    @Column(name = "param_value", nullable = false)
    private Double paramValue;

    @Column(length = 32)
    @Builder.Default
    private String algorithm = "RandomForest";

    private Double accuracy;

    @Column(name = "precision_val")
    private Double precisionVal;

    @Column(name = "recall_val")
    private Double recallVal;

    private Double f1;

    private Double mae;

    @Column(name = "create_time")
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();
}
