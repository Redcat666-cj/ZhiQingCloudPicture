package com.zhiqin.zhiqinpicturebackend.manager.table;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空间图片表管理器：按空间动态建表 + 维护「已存在哪些分表」。
 *
 * <p>表结构约定：</p>
 * <ul>
 *   <li>{@code picture_template} —— 建表模板，新空间的表都用 {@code CREATE TABLE ... LIKE} 它复制出来</li>
 *   <li>{@code picture_public}   —— 公共图库（spaceId 为 null 的图片）</li>
 *   <li>{@code picture_{spaceId}} —— 每个私有空间一张表</li>
 * </ul>
 *
 * <p>建表时机：</p>
 * <ol>
 *   <li>新建空间时（SpaceServiceImpl#addSpace）</li>
 *   <li>上传图片时兜底一次（防止历史空间漏建）</li>
 *   <li>应用启动时对已存在的空间做一次体检补建</li>
 * </ol>
 */
@Slf4j
@Component
public class SpaceTableManager implements ApplicationRunner {

    /**
     * 公共图库物理表
     */
    public static final String PUBLIC_TABLE = "picture_public";

    /**
     * 建表模板表
     */
    public static final String TEMPLATE_TABLE = "picture_template";

    private static final String TABLE_PREFIX = "picture_";

    @Resource
    private DataSource dataSource;

    /**
     * 已确认存在的物理表，避免每次上传都往数据库发一条 CREATE TABLE IF NOT EXISTS
     */
    private final Set<String> ensuredTables = ConcurrentHashMap.newKeySet();

    public static String tableNameOf(Long spaceId) {
        return TABLE_PREFIX + spaceId;
    }

    /**
     * 从物理表名解析空间 id，解析不出来返回 null
     */
    public static Long spaceIdOf(String tableName) {
        if (tableName == null || !tableName.startsWith(TABLE_PREFIX)) {
            return null;
        }
        String suffix = tableName.substring(TABLE_PREFIX.length());
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Long.valueOf(suffix);
    }

    /**
     * 确保该空间（spaceId 为 null 时是公共图库）的物理表存在
     */
    public void ensureSpaceTable(Long spaceId) {
        ensureTable(spaceId == null ? PUBLIC_TABLE : tableNameOf(spaceId));
    }

    /**
     * 确保物理表存在，不存在就用模板表复制一张
     */
    public void ensureTable(String tableName) {
        if (ensuredTables.contains(tableName)) {
            return;
        }
        synchronized (ensuredTables) {
            if (ensuredTables.contains(tableName)) {
                return;
            }
            try {
                if (!tableExists(tableName)) {
                    if (!tableExists(TEMPLATE_TABLE)) {
                        throw new IllegalStateException("缺少建表模板表 " + TEMPLATE_TABLE
                                + "，请先执行 sql/space_table_init.sql");
                    }
                    execute("CREATE TABLE `" + tableName + "` LIKE `" + TEMPLATE_TABLE + "`");
                    log.info("[动态分表] 已创建图片表 {}", tableName);
                }
                ensuredTables.add(tableName);
            } catch (SQLException e) {
                throw new IllegalStateException("创建图片表失败：" + tableName, e);
            }
        }
    }

    public boolean tableExists(String tableName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ? LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * 列出所有已存在的空间图片表（不含 picture_public / picture_template / 原 picture 表）
     */
    public List<String> listSpaceTables() {
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name LIKE 'picture\\_%' ORDER BY table_name";
        List<String> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String tableName = resultSet.getString(1);
                if (spaceIdOf(tableName) != null) {
                    result.add(tableName);
                }
            }
        } catch (SQLException e) {
            log.error("[动态分表] 查询空间图片表失败", e);
            return Collections.emptyList();
        }
        return result;
    }

    /**
     * 列出所有已存在的空间 id
     */
    public List<Long> listSpaceIds() {
        List<Long> result = new ArrayList<>();
        for (String tableName : listSpaceTables()) {
            Long spaceId = spaceIdOf(tableName);
            if (spaceId != null) {
                result.add(spaceId);
            }
        }
        return result;
    }

    /**
     * 启动时体检：保证模板表/公共表存在，并给 space 表里所有空间补建分表
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!tableExists(TEMPLATE_TABLE)) {
                log.error("[动态分表] 未找到 {}，请先执行 sql/space_table_init.sql，图片相关功能将不可用", TEMPLATE_TABLE);
                return;
            }
            ensureTable(PUBLIC_TABLE);
            for (String tableName : listSpaceTables()) {
                ensuredTables.add(tableName);
            }
            for (Long spaceId : listSpaceIdsFromSpaceTable()) {
                ensureSpaceTable(spaceId);
            }
            log.info("[动态分表] 初始化完成，空间分表数量：{}", listSpaceTables().size());
        } catch (Exception e) {
            log.error("[动态分表] 启动初始化失败", e);
        }
    }

    private List<Long> listSpaceIdsFromSpaceTable() {
        List<Long> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id FROM space")) {
            while (resultSet.next()) {
                result.add(resultSet.getLong(1));
            }
        } catch (SQLException e) {
            log.warn("[动态分表] 读取 space 表失败，跳过启动补建：{}", e.getMessage());
        }
        return result;
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
