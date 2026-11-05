-- maxTokens 改为档位化配置：8k / 16k / 32k / 64k
-- 历史配置统一归一到最近的档位，避免前端下拉出现空值
UPDATE ai_model_config
SET max_tokens = CASE
    WHEN max_tokens <= 8192 THEN 8192
    WHEN max_tokens <= 16384 THEN 16384
    WHEN max_tokens <= 32768 THEN 32768
    ELSE 65536
END;

ALTER TABLE ai_model_config MODIFY COLUMN max_tokens int NOT NULL DEFAULT 16384;
