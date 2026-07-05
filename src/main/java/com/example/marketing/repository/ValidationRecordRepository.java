package com.example.marketing.repository;

import com.example.marketing.entity.ValidationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidationRecordRepository extends JpaRepository<ValidationRecord, Long> {
    List<ValidationRecord> findBySysUserIdOrderByCreateTimeDesc(Long sysUserId);
}
