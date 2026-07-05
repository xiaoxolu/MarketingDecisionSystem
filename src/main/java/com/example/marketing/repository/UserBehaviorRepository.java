package com.example.marketing.repository;

import com.example.marketing.entity.UserBehavior;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserBehaviorRepository extends JpaRepository<UserBehavior, Long> {

    List<UserBehavior> findByBehaviorTimeBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT u.userId) FROM UserBehavior u")
    long countDistinctUsers();

    @Query("SELECT COUNT(DISTINCT u.productId) FROM UserBehavior u")
    long countDistinctProducts();

    @Query(value = "SELECT behavior_type, COUNT(*) as cnt FROM user_behavior GROUP BY behavior_type", nativeQuery = true)
    List<Object[]> countByBehaviorType();

    @Query(value = "SELECT DATE(behavior_time) as dt, COUNT(*) as cnt FROM user_behavior GROUP BY DATE(behavior_time) ORDER BY dt", nativeQuery = true)
    List<Object[]> countByDate();

    @Query(value = "SELECT user_id, COUNT(*) as cnt FROM user_behavior GROUP BY user_id ORDER BY cnt DESC LIMIT 10", nativeQuery = true)
    List<Object[]> findTop10ActiveUsers();

    @Query(value = "SELECT DATE_FORMAT(behavior_time, '%Y-%m') as mon, COUNT(*) as cnt FROM user_behavior GROUP BY mon ORDER BY mon", nativeQuery = true)
    List<Object[]> countByMonth();

    @Query(value = "SELECT MIN(behavior_time) FROM user_behavior", nativeQuery = true)
    String findMinBehaviorTime();

    @Query(value = "SELECT MAX(behavior_time) FROM user_behavior", nativeQuery = true)
    String findMaxBehaviorTime();
}
