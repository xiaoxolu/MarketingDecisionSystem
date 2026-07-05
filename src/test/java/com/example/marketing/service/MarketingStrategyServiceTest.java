package com.example.marketing.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketingStrategyServiceTest {

    private final MarketingStrategyService service = new MarketingStrategyService();

    @Test
    void shouldReturnHighIntentStrategy() {
        assertEquals("限时折扣+优先发货权益", service.getStrategyBySegment("高意向用户"));
    }

    @Test
    void shouldReturnMidIntentStrategy() {
        assertEquals("商品详情页+用户评价推送", service.getStrategyBySegment("中意向用户"));
    }

    @Test
    void shouldReturnLowIntentStrategy() {
        assertEquals("无门槛优惠券+新品试用", service.getStrategyBySegment("低意向用户"));
    }
}

