package com.zhiqin.zhiqinpicturebackend.picture.domain;

import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;

import java.util.Date;
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

    /**
     * 浏览数 +1 并刷新最近浏览时间（详情页每打开一次调用）。
     * SQL 层原子自增，避免读-改-写竞态。
     */
    void incrementReviews(Long id);

    /**
     * 标记图片的降沉状态（true=已降至低频存储，false=已恢复标准存储）
     */
    void markDemoted(Long id, boolean demoted);

    /**
     * 冷数据扫描：某张分表（spaceId 为 null 表示公共表）里
     * 最近浏览时间早于 threshold、且尚未降沉的图片。
     */
    List<Picture> listColdPictures(Date threshold, Long spaceId);
}
