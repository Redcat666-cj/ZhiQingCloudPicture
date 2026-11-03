package com.zhiqin.zhiqinpicturebackend.space.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceMemberRepository;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUser;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 空间成员仓储实现。
 */
@Repository
public class SpaceMemberRepositoryImpl implements SpaceMemberRepository {

    @Resource
    private SpaceUserMapper spaceUserMapper;

    @Override
    public SpaceUser findById(Long id) {
        return id == null ? null : toDomain(spaceUserMapper.selectById(id));
    }

    @Override
    public SpaceUser findBySpaceAndUser(Long spaceId, Long userId) {
        if (spaceId == null || userId == null) {
            return null;
        }
        QueryWrapper<SpaceUserPO> wrapper = new QueryWrapper<>();
        wrapper.eq("spaceId", spaceId);
        wrapper.eq("userId", userId);
        List<SpaceUserPO> list = spaceUserMapper.selectList(wrapper);
        return list.isEmpty() ? null : toDomain(list.get(0));
    }

    @Override
    public List<SpaceUser> list(Long id, Long spaceId, Long userId, String spaceRole) {
        QueryWrapper<SpaceUserPO> wrapper = new QueryWrapper<>();
        wrapper.eq(id != null, "id", id);
        wrapper.eq(spaceId != null, "spaceId", spaceId);
        wrapper.eq(userId != null, "userId", userId);
        wrapper.eq(spaceRole != null && !spaceRole.isEmpty(), "spaceRole", spaceRole);
        return spaceUserMapper.selectList(wrapper).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public SpaceUser save(SpaceUser spaceUser) {
        SpaceUserPO po = toPO(spaceUser);
        spaceUserMapper.insert(po);
        spaceUser.setId(po.getId());
        return spaceUser;
    }

    @Override
    public boolean update(SpaceUser spaceUser) {
        return spaceUserMapper.updateById(toPO(spaceUser)) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return spaceUserMapper.deleteById(id) > 0;
    }

    private SpaceUser toDomain(SpaceUserPO po) {
        if (po == null) {
            return null;
        }
        SpaceUser spaceUser = new SpaceUser();
        org.springframework.beans.BeanUtils.copyProperties(po, spaceUser);
        return spaceUser;
    }

    private SpaceUserPO toPO(SpaceUser spaceUser) {
        SpaceUserPO po = new SpaceUserPO();
        org.springframework.beans.BeanUtils.copyProperties(spaceUser, po);
        return po;
    }
}
