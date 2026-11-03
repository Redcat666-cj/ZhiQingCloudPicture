package com.zhiqin.zhiqinpicturebackend.space.application;

import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceMemberRepository;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceRepository;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUser;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 空间成员应用服务：成员增删改查。
 */
@Service
public class SpaceMemberAppService {

    @Resource
    private SpaceMemberRepository spaceMemberRepository;

    @Resource
    private SpaceRepository spaceRepository;

    @Resource
    private UserAppService userAppService;

    public SpaceUser getById(Long id) {
        return spaceMemberRepository.findById(id);
    }

    /**
     * 查某人在某空间的成员记录
     */
    public SpaceUser findBySpaceAndUser(Long spaceId, Long userId) {
        return spaceMemberRepository.findBySpaceAndUser(spaceId, userId);
    }

    public List<SpaceUser> list(Long id, Long spaceId, Long userId, String spaceRole) {
        return spaceMemberRepository.list(id, spaceId, userId, spaceRole);
    }

    /**
     * 添加成员：校验用户与空间是否存在
     */
    public long add(Long spaceId, Long userId, String spaceRole) {
        SpaceUser spaceUser = new SpaceUser();
        spaceUser.setSpaceId(spaceId);
        spaceUser.setUserId(userId);
        spaceUser.setSpaceRole(spaceRole);
        validSpaceUser(spaceUser, true);
        if (userAppService.getById(userId) == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户不存在");
        }
        Space space = spaceRepository.findById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不存在");
        SpaceUser saved = spaceMemberRepository.save(spaceUser);
        return saved.getId();
    }

    public boolean save(SpaceUser spaceUser) {
        return spaceMemberRepository.save(spaceUser) != null;
    }

    public boolean update(SpaceUser spaceUser) {
        return spaceMemberRepository.update(spaceUser);
    }

    public boolean removeById(Long id) {
        return spaceMemberRepository.removeById(id);
    }

    /**
     * 成员数据校验（创建时空间 id、用户 id 必填，角色必须是合法枚举）
     */
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        if (add && (spaceUser.getSpaceId() == null || spaceUser.getUserId() == null)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        spaceUser.validate(add);
    }
}
