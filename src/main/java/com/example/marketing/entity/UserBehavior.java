package com.example.marketing.entity;

import javax.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_behavior")
public class UserBehavior {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "behavior_type", nullable = false, length = 16)
    private String behaviorType; // 浏览/加购/下单/评价

    @Column(name = "behavior_time", nullable = false)
    private LocalDateTime behaviorTime;

    @Column(name = "page_duration")
    private Integer pageDuration;
}

