package com.zhiqin.zhiqinpicturebackend.space.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceQuery;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 空间仓储实现：PO ↔ 领域对象转换 + 查询条件翻译。
 */
@Slf4j
@Repository
public class SpaceRepositoryImpl implements SpaceRepository {

    @Resource
    private SpaceMapper spaceMapper;

    @Override
    public Space findById(Long id) {
        return id == null ? null : toDomain(spaceMapper.selectById(id));
    }

    @Override
    public List<Space> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return spaceMapper.selectBatchIds(ids).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Space save(Space space) {
        SpacePO po = toPO(space);
        spaceMapper.insert(po);
        space.setId(po.getId());
        return space;
    }

    @Override
    public boolean update(Space space) {
        return spaceMapper.updateById(toPO(space)) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return spaceMapper.deleteById(id) > 0;
    }

    @Override
    public PageResult<Space> page(SpaceQuery query) {
        QueryWrapper<SpacePO> wrapper = new QueryWrapper<>();
        wrapper.eq(query.getId() != null, "id", query.getId());
        wrapper.eq(query.getUserId() != null, "userId", query.getUserId());
        wrapper.like(query.getSpaceName() != null && !query.getSpaceName().isEmpty(), "spaceName", query.getSpaceName());
        wrapper.eq(query.getSpaceLevel() != null, "spaceLevel", query.getSpaceLevel());
        wrapper.eq(query.getSpaceType() != null, "spaceType", query.getSpaceType());
        if (query.getSortField() != null && !query.getSortField().isEmpty()) {
            wrapper.orderBy(true, query.isAscend(), query.getSortField());
        }
        Page<SpacePO> page = spaceMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toDomain).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public boolean existsByUserAndType(Long userId, Integer spaceType) {
        QueryWrapper<SpacePO> wrapper = new QueryWrapper<>();
        wrapper.eq("userId", userId);
        wrapper.eq(spaceType != null, "spaceType", spaceType);
        return spaceMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<Space> listTopByUsedSize(int topN) {
        QueryWrapper<SpacePO> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("totalSize").last("LIMIT " + Math.max(1, topN));
        return spaceMapper.selectList(wrapper).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public boolean changeUsage(Long spaceId, long sizeDelta, long countDelta) {
        UpdateWrapper<SpacePO> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", spaceId);
        // 用 SQL 原子增减，避免读改写丢更新
        wrapper.setSql("totalSize = totalSize + (" + sizeDelta + ")");
        wrapper.setSql("totalCount = totalCount + (" + countDelta + ")");
        return spaceMapper.update(null, wrapper) > 0;
    }

    private Space toDomain(SpacePO po) {
        if (po == null) {
            return null;
        }
        Space space = new Space();
        org.springframework.beans.BeanUtils.copyProperties(po, space);
        return space;
    }

    private SpacePO toPO(Space space) {
        SpacePO po = new SpacePO();
        org.springframework.beans.BeanUtils.copyProperties(space, po);
        return po;
    }
}
