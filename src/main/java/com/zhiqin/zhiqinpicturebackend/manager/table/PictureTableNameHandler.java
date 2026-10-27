package com.zhiqin.zhiqinpicturebackend.manager.table;

import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import org.springframework.stereotype.Component;

/**
 * 动态表名处理器：把逻辑表 picture 换成真实的物理表。
 *
 * <ul>
 *   <li>上下文 spaceId 为 null → picture_public（公共图库）</li>
 *   <li>上下文 spaceId 不为 null → picture_{spaceId}（空间私有表）</li>
 * </ul>
 *
 * <p>注册见 {@code MyBatisPlusConfig}，它是所有 SQL（MP 的 Wrapper、BaseMapper、
 * 自定义 XML）的统一入口，业务代码依然只写 picture。</p>
 */
@Component
public class PictureTableNameHandler implements TableNameHandler {

    /**
     * 逻辑表名，和 Picture 实体上的 @TableName("picture") 保持一致
     */
    public static final String LOGIC_TABLE_NAME = "picture";

    @Override
    public String dynamicTableName(String sql, String tableName) {
        // 只改写逻辑表 picture，其它表（user / space / space_user / picture_xxx）原样返回
        if (!LOGIC_TABLE_NAME.equalsIgnoreCase(tableName)) {
            return tableName;
        }
        Long spaceId = SpaceTableContext.get();
        return spaceId == null
                ? SpaceTableManager.PUBLIC_TABLE
                : SpaceTableManager.tableNameOf(spaceId);
    }
}
