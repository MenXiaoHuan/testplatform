-- 样本级判定关键词：关键词匹配评估器优先使用该字段，便于整批执行无需逐条配置
alter table eval_dataset_sample add column keywords text null after golden_answer_versions;

-- 评测集级推荐评估器配置：启动评测时自动带入，可手动覆盖
alter table eval_dataset add column recommended_evaluators text null after recommended_target_types;
