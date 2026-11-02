-- 巡页：模型可见页面文本字数上限，规划与判定统一使用该配置。
ALTER TABLE patrol_scene
    ADD COLUMN visible_text_limit int NOT NULL DEFAULT 1800
        COMMENT '模型可见页面文本字数上限' AFTER budget_llm_tokens;
