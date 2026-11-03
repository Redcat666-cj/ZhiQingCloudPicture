package com.zhiqin.zhiqinpicturebackend.picture.domain;

import java.util.List;
import java.util.function.Supplier;

/**
 * 图片分表路由（领域端口）。
 *
 * <p>图片表是按空间拆分的：<code>picture_public</code> + <code>picture_{spaceId}</code>。
 * 领域层只需要"在某个空间里做一件事"，不关心底层是 ThreadLocal 还是 MyBatis 拦截器。</p>
 */
public interface PictureTableRouter {

    /**
     * 反查图片属于哪个空间（公共图库返回 null）
     */
    Long locateSpaceId(Long pictureId);

    /**
     * 在指定空间的物理表上执行一段逻辑（spaceId 为 null 表示公共图库）
     */
    <T> T inSpace(Long spaceId, Supplier<T> action);

    /**
     * 当前线程绑定的空间 id（无绑定返回 null）
     */
    Long currentSpaceId();

    /**
     * 确保该空间的物理表存在（新空间上传第一张图时兜底）
     */
    void ensureSpaceTable(Long spaceId);

    /**
     * 已存在的所有空间 id
     */
    List<Long> listSpaceIds();

    /**
     * 所有查询范围：公共图库（null）+ 每个空间
     */
    List<Long> allScopes();
}
