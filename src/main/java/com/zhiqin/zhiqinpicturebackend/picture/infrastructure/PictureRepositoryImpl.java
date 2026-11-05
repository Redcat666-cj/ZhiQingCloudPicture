package com.zhiqin.zhiqinpicturebackend.picture.infrastructure;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiqin.zhiqinpicturebackend.picture.domain.Picture;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureQuery;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureRepository;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureTableRouter;
import com.zhiqin.zhiqinpicturebackend.picture.infrastructure.sharding.PictureTableLocator;
import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 图片仓储实现。
 *
 * <p>两类查询：</p>
 * <ul>
 *   <li>单空间：交给 MyBatis-Plus 分页，SQL 只打一张物理表</li>
 *   <li>跨空间（管理员全局列表）：逐表查出来合并、重排、再截页——这是"每个空间一张表"的固有代价</li>
 * </ul>
 */
@Repository
public class PictureRepositoryImpl implements PictureRepository {

    @Resource
    private PictureMapper PictureMapper;

    @Resource
    private PictureTableRouter pictureTableRouter;

    @Resource
    private PictureTableLocator pictureTableLocator;

    @Override
    public Picture findById(Long id) {
        if (id == null) {
            return null;
        }
        Long spaceId = pictureTableRouter.locateSpaceId(id);
        return pictureTableRouter.inSpace(spaceId, () -> toDomain(PictureMapper.selectById(id)));
    }

    @Override
    public Picture save(Picture picture) {
        if (picture.getId() == null) {
            // 新空间第一次上传时兜底建表
            pictureTableRouter.ensureSpaceTable(picture.getSpaceId());
            return pictureTableRouter.inSpace(picture.getSpaceId(), () -> {
                PicturePO po = toPO(picture);
                PictureMapper.insert(po);
                picture.setId(po.getId());
                return picture;
            });
        }
        return update(picture) ? picture : null;
    }

    @Override
    public boolean update(Picture picture) {
        Long spaceId = picture.getSpaceId() != null
                ? picture.getSpaceId()
                : pictureTableRouter.locateSpaceId(picture.getId());
        return pictureTableRouter.inSpace(spaceId, () -> PictureMapper.updateById(toPO(picture)) > 0);
    }

    @Override
    public boolean removeById(Long id) {
        Long spaceId = pictureTableRouter.locateSpaceId(id);
        return pictureTableRouter.inSpace(spaceId, () -> {
            boolean removed = PictureMapper.deleteById(id) > 0;
            if (removed) {
                pictureTableLocator.evict(id);
            }
            return removed;
        });
    }

    @Override
    public PageResult<Picture> page(PictureQuery query) {
        if (query.isCrossSpaces()) {
            return pageAcrossSpaces(query);
        }
        return pictureTableRouter.inSpace(query.getSpaceId(), () -> {
            Page<PicturePO> page = PictureMapper.selectPage(
                    new Page<>(query.getCurrent(), query.getSize()), buildWrapper(query));
            return PageResult.of(page.getRecords().stream().map(this::toDomain).collect(Collectors.toList()),
                    page.getTotal(), page.getCurrent(), page.getSize());
        });
    }

    @Override
    public List<Picture> listBySpace(Long spaceId) {
        return pictureTableRouter.inSpace(spaceId,
                () -> PictureMapper.selectList(buildWrapper(new PictureQuery())).stream()
                        .map(this::toDomain).collect(Collectors.toList()));
    }

    // ==================== 浏览数与冷热分层 ====================

    @Override
    public void incrementReviews(Long id) {
        Long spaceId = pictureTableRouter.locateSpaceId(id);
        pictureTableRouter.inSpace(spaceId, () -> {
            // SQL 层原子自增，避免"查出来加一再写回"的读-改-写竞态
            UpdateWrapper<PicturePO> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", id)
                    .setSql("reviews = reviews + 1")
                    .set("lastViewTime", new Date());
            PictureMapper.update(null, wrapper);
            return null;
        });
    }

    @Override
    public void markDemoted(Long id, boolean demoted) {
        Long spaceId = pictureTableRouter.locateSpaceId(id);
        pictureTableRouter.inSpace(spaceId, () -> {
            UpdateWrapper<PicturePO> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", id).set("demoted", demoted ? 1 : 0);
            PictureMapper.update(null, wrapper);
            return null;
        });
    }

    @Override
    public List<Picture> listColdPictures(Date threshold, Long spaceId) {
        return pictureTableRouter.inSpace(spaceId, () -> {
            PictureQuery query = new PictureQuery();
            query.setLastViewBefore(threshold);
            if (spaceId == null) {
                query.setNullSpaceId(true);
            } else {
                query.setSpaceId(spaceId);
            }
            return PictureMapper.selectList(buildWrapper(query)).stream()
                    .map(this::toDomain).collect(Collectors.toList());
        });
    }

    // ==================== 跨空间合并分页 ====================

    private PageResult<Picture> pageAcrossSpaces(PictureQuery query) {
        List<Picture> all = new ArrayList<>();
        for (Long scope : pictureTableRouter.allScopes()) {
            all.addAll(pictureTableRouter.inSpace(scope,
                    () -> PictureMapper.selectList(buildWrapper(query)).stream()
                            .map(this::toDomain).collect(Collectors.toList())));
        }
        all.sort(buildComparator(query.getSortField(), query.getSortOrder()));
        long from = Math.max(0, (query.getCurrent() - 1) * query.getSize());
        int to = (int) Math.min(all.size(), from + query.getSize());
        List<Picture> records = from >= all.size() ? new ArrayList<>() : new ArrayList<>(all.subList((int) from, to));
        return PageResult.of(records, all.size(), query.getCurrent(), query.getSize());
    }

    private Comparator<Picture> buildComparator(String sortField, String sortOrder) {
        boolean ascend = "ascend".equalsIgnoreCase(sortOrder);
        Comparator<Picture> comparator;
        // 跨表归并的默认排序与单表保持一致：按浏览数热度，id 兜底
        String field = StrUtil.blankToDefault(sortField, "reviews");
        switch (field) {
            case "createTime":
                comparator = byAsc(Picture::getCreateTime);
                break;
            case "editTime":
                comparator = byAsc(Picture::getEditTime);
                break;
            case "updateTime":
                comparator = byAsc(Picture::getUpdateTime);
                break;
            case "name":
                comparator = byAsc(Picture::getName);
                break;
            case "picSize":
                comparator = byAsc(Picture::getPicSize);
                break;
            case "reviews":
                comparator = byAsc(Picture::getReviews).thenComparing(byAsc(Picture::getId));
                break;
            case "id":
                comparator = byAsc(Picture::getId);
                break;
            default:
                comparator = byAsc(Picture::getReviews).thenComparing(byAsc(Picture::getId));
                break;
        }
        return ascend ? comparator : comparator.reversed();
    }

    private <U extends Comparable<? super U>> Comparator<Picture> byAsc(Function<Picture, U> getter) {
        return Comparator.comparing(getter, Comparator.nullsLast(Comparator.<U>naturalOrder()));
    }

    // ==================== 查询条件翻译 ====================

    private QueryWrapper<PicturePO> buildWrapper(PictureQuery query) {
        QueryWrapper<PicturePO> wrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(query.getSearchText())) {
            String searchText = query.getSearchText();
            wrapper.and(qw -> qw.like("name", searchText).or().like("introduction", searchText));
        }
        wrapper.eq(query.getId() != null, "id", query.getId());
        wrapper.eq(query.getUserId() != null, "userId", query.getUserId());
        wrapper.eq(query.getSpaceId() != null, "spaceId", query.getSpaceId());
        wrapper.isNull(query.isNullSpaceId(), "spaceId");
        wrapper.like(StrUtil.isNotBlank(query.getName()), "name", query.getName());
        wrapper.like(StrUtil.isNotBlank(query.getIntroduction()), "introduction", query.getIntroduction());
        wrapper.like(StrUtil.isNotBlank(query.getPicFormat()), "picFormat", query.getPicFormat());
        wrapper.like(StrUtil.isNotBlank(query.getReviewMessage()), "reviewMessage", query.getReviewMessage());
        wrapper.eq(StrUtil.isNotBlank(query.getCategory()), "category", query.getCategory());
        wrapper.eq(query.getPicWidth() != null, "picWidth", query.getPicWidth());
        wrapper.eq(query.getPicHeight() != null, "picHeight", query.getPicHeight());
        wrapper.eq(query.getPicSize() != null, "picSize", query.getPicSize());
        wrapper.eq(query.getPicScale() != null, "picScale", query.getPicScale());
        wrapper.eq(query.getReviewStatus() != null, "reviewStatus", query.getReviewStatus());
        wrapper.eq(query.getReviewerId() != null, "reviewerId", query.getReviewerId());
        wrapper.ge(query.getStartEditTime() != null, "editTime", query.getStartEditTime());
        wrapper.lt(query.getEndEditTime() != null, "editTime", query.getEndEditTime());
        if (query.getTags() != null) {
            for (String tag : query.getTags()) {
                wrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 冷数据扫描条件：未降沉 + 最近浏览早于阈值（含从未浏览过的 NULL）
        if (query.getLastViewBefore() != null) {
            wrapper.eq("demoted", 0)
                    .and(qw -> qw.lt("lastViewTime", query.getLastViewBefore())
                            .or().isNull("lastViewTime"));
        }
        applySort(wrapper, query.getSortField(), query.isAscend());
        return wrapper;
    }

    /**
     * 排序字段白名单：sortField 来自前端请求，直接拼进 orderBy 是 SQL 注入点。
     * 不在白名单内（含空）时走默认热度排序：reviews 降序，id 降序兜底保证稳定。
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "reviews", "createTime", "editTime", "updateTime", "name", "picSize", "picWidth", "picHeight", "id");

    private void applySort(QueryWrapper<PicturePO> wrapper, String sortField, boolean ascend) {
        if (StrUtil.isNotBlank(sortField) && SORTABLE_FIELDS.contains(sortField)) {
            wrapper.orderBy(true, ascend, sortField);
            if (!"id".equals(sortField)) {
                wrapper.orderByDesc("id");
            }
            return;
        }
        // 默认：按浏览数热度排序
        wrapper.orderByDesc("reviews");
        wrapper.orderByDesc("id");
    }

    private Picture toDomain(PicturePO po) {
        if (po == null) {
            return null;
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(po, picture);
        return picture;
    }

    private PicturePO toPO(Picture picture) {
        PicturePO po = new PicturePO();
        BeanUtils.copyProperties(picture, po);
        return po;
    }
}
