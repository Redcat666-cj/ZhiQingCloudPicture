-- ============================================================================
-- picture 按空间动态分表 · 初始化脚本
--
-- 思路：单库 + MyBatis-Plus 动态表名（DynamicTableNameInnerInterceptor）
--   逻辑表 picture 在运行期被改写成：
--     spaceId 为 null → picture_public          公共图库
--     spaceId 不为 null → picture_{spaceId}      每个空间一张私有表
--
-- 本脚本负责：建模板表、建公共表、给「已存在的」空间各建一张表，并迁移历史数据。
-- 之后新建的空间由应用自动建表（SpaceServiceImpl#addSpace → SpaceTableManager#ensureSpaceTable），
-- 不需要再改脚本、改配置、重启。
--
-- 原始 picture 表不做任何修改，保留作为回滚备份。
-- 执行：mysql -uroot -p < sql/space_table_init.sql
-- ============================================================================
USE zhiqin_picture;

-- ---------------------------------------------------------------------------
-- 1. 建表模板：以后每个新空间的表都是 CREATE TABLE ... LIKE picture_template
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS picture_template LIKE picture;

-- ---------------------------------------------------------------------------
-- 2. 公共图库表（spaceId 为 null 的图片）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS picture_public LIKE picture_template;

-- ---------------------------------------------------------------------------
-- 3. 给已存在的空间各建一张表（当前库里共 4 个空间）
--    新增空间不再需要手工执行，应用会自动建
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS picture_2107005298801410050 LIKE picture_template;
CREATE TABLE IF NOT EXISTS picture_2107006929714577409 LIKE picture_template;
CREATE TABLE IF NOT EXISTS picture_2107048874356846594 LIKE picture_template;
CREATE TABLE IF NOT EXISTS picture_2112921249988239361 LIKE picture_template;

-- ---------------------------------------------------------------------------
-- 4. 迁移历史数据（可重复执行）
-- ---------------------------------------------------------------------------
TRUNCATE TABLE picture_public;
INSERT INTO picture_public
SELECT * FROM picture WHERE spaceId IS NULL;

TRUNCATE TABLE picture_2107005298801410050;
INSERT INTO picture_2107005298801410050
SELECT * FROM picture WHERE spaceId = 2107005298801410050;

TRUNCATE TABLE picture_2107006929714577409;
INSERT INTO picture_2107006929714577409
SELECT * FROM picture WHERE spaceId = 2107006929714577409;

TRUNCATE TABLE picture_2107048874356846594;
INSERT INTO picture_2107048874356846594
SELECT * FROM picture WHERE spaceId = 2107048874356846594;

TRUNCATE TABLE picture_2112921249988239361;
INSERT INTO picture_2112921249988239361
SELECT * FROM picture WHERE spaceId = 2112921249988239361;

-- ---------------------------------------------------------------------------
-- 5. 校验：各分表行数之和应等于原表行数
-- ---------------------------------------------------------------------------
SELECT 'picture(原始)' AS tbl, COUNT(*) AS cnt FROM picture
UNION ALL SELECT 'picture_public', COUNT(*) FROM picture_public
UNION ALL SELECT 'picture_2107005298801410050', COUNT(*) FROM picture_2107005298801410050
UNION ALL SELECT 'picture_2107006929714577409', COUNT(*) FROM picture_2107006929714577409
UNION ALL SELECT 'picture_2107048874356846594', COUNT(*) FROM picture_2107048874356846594
UNION ALL SELECT 'picture_2112921249988239361', COUNT(*) FROM picture_2112921249988239361;
