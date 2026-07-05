package com.example.marketing.service;

import com.example.marketing.entity.SysUser;
import com.example.marketing.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    private final SysUserRepository sysUserRepository;

    public UserService(SysUserRepository sysUserRepository) {
        this.sysUserRepository = sysUserRepository;
    }

    public SysUser register(String username, String password) {
        if (sysUserRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        if (password == null || password.length() < 8) {
            throw new RuntimeException("密码长度不能少于8位");
        }
        if (password.matches("^\\d+$")) {
            throw new RuntimeException("密码不能全是数字，请包含字母或特殊字符");
        }
        SysUser user = SysUser.builder()
                .username(username)
                .password(password)
                .build();
        return sysUserRepository.save(user);
    }

    public SysUser login(String username, String password) {
        Optional<SysUser> opt = sysUserRepository.findByUsername(username);
        if (!opt.isPresent()) throw new RuntimeException("用户不存在");
        SysUser user = opt.get();
        if (!user.getPassword().equals(password)) throw new RuntimeException("密码错误");
        return user;
    }

    public SysUser getById(Long id) {
        return sysUserRepository.findById(id).orElse(null);
    }

    @Transactional
    public SysUser recharge(Long userId, BigDecimal amount) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setBalance(user.getBalance().add(amount));
        return sysUserRepository.save(user);
    }

    @Transactional
    public SysUser subscribe(Long userId, int planType) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        BigDecimal price;
        int vipLevel;
        int months;

        if (planType == 1) {
            price = new BigDecimal("99.00");
            vipLevel = 1;
            months = 1;
        } else if (planType == 2) {
            price = new BigDecimal("899.00");
            vipLevel = 2;
            months = 12;
        } else {
            throw new RuntimeException("无效的套餐类型");
        }

        if (user.getBalance().compareTo(price) < 0) {
            throw new RuntimeException("余额不足，请先充值。当前余额：" + user.getBalance() + "元");
        }

        user.setBalance(user.getBalance().subtract(price));
        user.setVipLevel(vipLevel);

        LocalDateTime base = (user.isVipActive()) ? user.getVipExpireTime() : LocalDateTime.now();
        user.setVipExpireTime(base.plusMonths(months));

        return sysUserRepository.save(user);
    }
}
