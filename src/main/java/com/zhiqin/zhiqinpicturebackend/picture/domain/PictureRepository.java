package com.zhiqin.zhiqinpicturebackend.picture.domain;

import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;

import java.util.List;

/**
 * 图片仓储（领域端口）。
 *
 * <p>实现见 {@code picture.infrastructure.PictureRepositoryImpl}，内部会根据
 * {@link PictureTableRouter} 把逻辑表 picture 路由到真实物理表。</p>
 */
public interface PictureRepository {

    Picture findById(Long id);

    /**
     * 新增或更新（id 为空走新增）
     */
    Picture save(Picture picture);

    boolean update(Picture picture);

    boolean removeById(Long id);

    /**
     * 条件分页：单空间分页 or 跨空间合并分页（由 query.crossSpaces 决定）
     */
    PageResult<Picture> page(PictureQuery query);

    /**
     * 查某空间下的全部图片（空间分析用，量小）
     */
    List<Picture> listBySpace(Long spaceId);
}
