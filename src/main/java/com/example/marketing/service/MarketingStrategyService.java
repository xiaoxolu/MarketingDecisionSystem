package com.example.marketing.service;

import org.springframework.stereotype.Service;

@Service
public class MarketingStrategyService {
    public String getStrategyBySegment(String segment) {
        if ("高意向用户".equals(segment)) {
            return "限时折扣+优先发货权益";
        }
        if ("中意向用户".equals(segment)) {
            return "商品详情页+用户评价推送";
        }
        if ("低意向用户".equals(segment)) {
            return "无门槛优惠券+新品试用";
        }
        return "暂无策略";
    }
}

