-- ============================================================
-- 图片冷热分层迁移脚本：reviews(浏览数) / lastViewTime(最近浏览) / demoted(是否降沉)
-- 适用于已按 space_table_init.sql 建过分表的存量库；全新安装直接用最新 create_table.sql 即可
-- ============================================================
USE zhiqin_picture;

-- 1. 模板表与公共表
ALTER TABLE picture_template
    ADD COLUMN reviews      bigint DEFAULT 0 NOT NULL COMMENT '浏览数',
    ADD COLUMN lastViewTime datetime DEFAULT NULL COMMENT '最近一次浏览时间（reviews 最后一次变化）',
    ADD COLUMN demoted      tinyint DEFAULT 0 NOT NULL COMMENT '是否已降沉至低频存储 0-否 1-是',
    ADD INDEX idx_lastViewTime (lastViewTime);

ALTER TABLE picture_public
    ADD COLUMN reviews      bigint DEFAULT 0 NOT NULL COMMENT '浏览数',
    ADD COLUMN lastViewTime datetime DEFAULT NULL COMMENT '最近一次浏览时间（reviews 最后一次变化）',
    ADD COLUMN demoted      tinyint DEFAULT 0 NOT NULL COMMENT '是否已降沉至低频存储 0-否 1-是',
    ADD INDEX idx_lastViewTime (lastViewTime);

-- 2. 动态遍历所有已存在的空间分表（picture_ 前缀，排除公共表与模板表）
DELIMITER $$
DROP PROCEDURE IF EXISTS add_picture_demote_columns $$
CREATE PROCEDURE add_picture_demote_columns()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE tbl VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = 'zhiqin_picture'
          AND table_name LIKE 'picture[_]%'
          AND table_name NOT IN ('picture_public', 'picture_template');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO tbl;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;
        SET @ddl = CONCAT(
            'ALTER TABLE `', tbl, '` ',
            'ADD COLUMN reviews bigint DEFAULT 0 NOT NULL COMMENT \'浏览数\', ',
            'ADD COLUMN lastViewTime datetime DEFAULT NULL COMMENT \'最近一次浏览时间\', ',
            'ADD COLUMN demoted tinyint DEFAULT 0 NOT NULL COMMENT \'是否已降沉至低频存储 0-否 1-是\', ',
            'ADD INDEX idx_lastViewTime (lastViewTime)');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;
END $$
DELIMITER ;

CALL add_picture_demote_columns();
DROP PROCEDURE add_picture_demote_columns;

-- 3. 验证：三列应存在于所有 picture% 表
SELECT table_name, column_name FROM information_schema.columns
WHERE table_schema = 'zhiqin_picture'
  AND column_name IN ('reviews', 'lastViewTime', 'demoted')
ORDER BY table_name, column_name;
