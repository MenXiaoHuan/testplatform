-- =====================================================================
-- V23 巡页探索策略配置
-- 1) 每页元素采集上限、弹层元素上限、截图上限可在新建时配置。
-- 2) 滚动预扫描、hover 探测、下拉框全选项遍历可开关。
-- =====================================================================

ALTER TABLE patrol_task
    ADD COLUMN max_elements_per_page int NOT NULL DEFAULT 80 AFTER max_steps,
    ADD COLUMN max_modal_elements int NOT NULL DEFAULT 8 AFTER max_elements_per_page,
    ADD COLUMN max_screenshots int NOT NULL DEFAULT 200 AFTER max_modal_elements,
    ADD COLUMN scroll_scan tinyint(1) NOT NULL DEFAULT 1 AFTER max_screenshots,
    ADD COLUMN hover_probe tinyint(1) NOT NULL DEFAULT 1 AFTER scroll_scan,
    ADD COLUMN select_all_options tinyint(1) NOT NULL DEFAULT 0 AFTER hover_probe;
