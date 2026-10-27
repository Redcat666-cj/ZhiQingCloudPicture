package com.zhiqin.zhiqinpicturebackend.manager.table;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

/**
 * 图片 → 空间 定位器。
 *
 * <p>动态分表之后，只给一个图片 id（比如 GET /picture/get/vo?id=xxx、审核、删除）
 * 是不知道该查哪张物理表的，这里负责反查它属于哪个空间：</p>
 *
 * <ol>
 *   <li>先查公共表 picture_public；</li>
 *   <li>再依次查各空间的 picture_{spaceId}；</li>
 *   <li>查到的结果放进 Caffeine 缓存（id → spaceId），之后不再扫表。</li>
 * </ol>
 *
 * <p>缓存用 0 表示「确实在公共图库」（Caffeine 不允许存 null）；
 * 一次都没找到的 id 不写缓存，避免把某个「刚创建、还没落库」的 id 误判成公共图库。</p>
 */
@Slf4j
@Component
public class PictureTableLocator {

    /**
     * 缓存哨兵值：查到了，但属于公共图库（spaceId 为 null）
     */
    private static final Long PUBLIC_SENTINEL = 0L;

    private final Cache<Long, Long> pictureSpaceCache = Caffeine.newBuilder()
            .maximumSize(200_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    @Resource
    private DataSource dataSource;

    @Resource
    private SpaceTableManager spaceTableManager;

    /**
     * 反查图片所属空间
     *
     * @return 空间 id；null 表示公共图库（或图片不存在）
     */
    public Long locateSpaceId(Long pictureId) {
        if (pictureId == null) {
            return null;
        }
        Long cached = pictureSpaceCache.getIfPresent(pictureId);
        if (cached != null) {
            return PUBLIC_SENTINEL.equals(cached) ? null : cached;
        }
        Long located = scan(pictureId);
        if (located == null) {
            // 哪张表都没查到，不写缓存：可能是一张刚创建、尚未落库的图片
            return null;
        }
        pictureSpaceCache.put(pictureId, located);
        return PUBLIC_SENTINEL.equals(located) ? null : located;
    }

    /**
     * 图片删除、换空间后清掉缓存
     */
    public void evict(Long pictureId) {
        if (pictureId != null) {
            pictureSpaceCache.invalidate(pictureId);
        }
    }

    /**
     * @return PUBLIC_SENTINEL 表示在公共图库；空间 id 表示在对应空间；null 表示没找到
     */
    private Long scan(Long pictureId) {
        if (existsInTable(SpaceTableManager.PUBLIC_TABLE, pictureId)) {
            return PUBLIC_SENTINEL;
        }
        for (Long spaceId : spaceTableManager.listSpaceIds()) {
            if (existsInTable(SpaceTableManager.tableNameOf(spaceId), pictureId)) {
                return spaceId;
            }
        }
        return null;
    }

    private boolean existsInTable(String tableName, Long pictureId) {
        String sql = "SELECT 1 FROM `" + tableName + "` WHERE id = ? LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, pictureId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            log.warn("[动态分表] 查询表 {} 失败：{}", tableName, e.getMessage());
            return false;
        }
    }
}
