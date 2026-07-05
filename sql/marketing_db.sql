CREATE DATABASE IF NOT EXISTS marketing_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE marketing_db;

CREATE TABLE IF NOT EXISTS user_behavior (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL,
  product_id VARCHAR(64) NOT NULL,
  behavior_type VARCHAR(16) NOT NULL COMMENT '取值：浏览/加购/下单/评价',
  behavior_time DATETIME NOT NULL,
  page_duration INT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS feature_dataset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL,
  browse_duration_avg DOUBLE NULL,
  cart_frequency DOUBLE NULL,
  purchase_rate DOUBLE NULL,
  review_score_avg DOUBLE NULL,
  product_hot_level INT NULL,
  product_category INT NULL,
  product_price_range INT NULL,
  age_group INT NULL,
  consume_level INT NULL,
  city_level INT NULL,
  is_willing_purchase INT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) UNIQUE NOT NULL,
  password VARCHAR(128) NOT NULL,
  balance DECIMAL(10,2) DEFAULT 0.00,
  vip_level INT DEFAULT 0 COMMENT '0=免费,1=月付会员,2=年付会员',
  vip_expire_time DATETIME NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS validation_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sys_user_id BIGINT NOT NULL,
  param_value DOUBLE NOT NULL COMMENT '决策阈值参数',
  algorithm VARCHAR(32) DEFAULT 'RandomForest',
  accuracy DOUBLE,
  precision_val DOUBLE,
  recall_val DOUBLE,
  f1 DOUBLE,
  mae DOUBLE,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
