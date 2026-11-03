package com.zhiqin.zhiqinpicturebackend.space.domain;

import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * 空间仓储（领域端口）。
 */
public interface SpaceRepository {

    Space findById(Long id);

    List<Space> findByIds(Collection<Long> ids);

    /**
     * 新增空间，成功后把主键写回
     */
    Space save(Space space);

    /**
     * 按主键更新非空字段
     */
    boolean update(Space space);

    /**
     * 按主键逻辑删除
     */
    boolean removeById(Long id);

    /**
     * 条件分页
     */
    PageResult<Space> page(SpaceQuery query);

    /**
     * 某用户在某类型下是否已有空间
     */
    boolean existsByUserAndType(Long userId, Integer spaceType);

    /**
     * 按已用容量取前 N 名（空间排行）
     */
    List<Space> listTopByUsedSize(int topN);

    /**
     * 原子增减用量（上传 +1、删除 -1）
     */
    boolean changeUsage(Long spaceId, long sizeDelta, long countDelta);
}
