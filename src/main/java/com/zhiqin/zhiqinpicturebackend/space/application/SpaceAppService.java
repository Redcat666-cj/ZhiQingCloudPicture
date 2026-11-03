package com.zhiqin.zhiqinpicturebackend.space.application;

import cn.hutool.core.util.StrUtil;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.picture.infrastructure.sharding.SpaceTableManager;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.space.application.command.AddSpaceCommand;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceLevelEnum;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceMemberRepository;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceQuery;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceRepository;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceRoleEnum;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceTypeEnum;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空间应用服务：空间生命周期的用例编排。
 *
 * <p>建空间这条用例涉及三件事，顺序不能乱：</p>
 * <ol>
 *   <li>锁 + 事务内完成「唯一性校验 → 落库 → 团队空间补创建者成员」</li>
 *   <li>事务提交后再建分表（DDL 会隐式提交，不能放进事务）</li>
 * </ol>
 */
@Service
public class SpaceAppService {

    private static final String DEFAULT_SPACE_NAME = "默认空间";

    @Resource
    private SpaceRepository spaceRepository;

    @Resource
    private SpaceMemberRepository spaceMemberRepository;

    @Resource
    private SpaceTableManager spaceTableManager;

    @Resource
    private UserAppService userAppService;

    @Resource
    private TransactionTemplate transactionTemplate;

    private final ConcurrentHashMap<Long, Object> lockMap = new ConcurrentHashMap<>();

    /**
     * 创建空间
     */
    public long addSpace(AddSpaceCommand command, User loginUser) {
        ThrowUtils.throwIf(command == null, ErrorCode.PARAMS_ERROR);
        Long userId = loginUser.getId();
        Space space = new Space();
        space.setSpaceName(StrUtil.isBlank(command.getSpaceName()) ? DEFAULT_SPACE_NAME : command.getSpaceName());
        space.setSpaceLevel(command.getSpaceLevel() == null ? SpaceLevelEnum.COMMON.getValue() : command.getSpaceLevel());
        space.setSpaceType(command.getSpaceType() == null ? SpaceTypeEnum.PRIVATE.getValue() : command.getSpaceType());
        space.setUserId(userId);
        // 领域校验
        space.validate(true);
        // 按级别补齐额度（用户显式传了限额就不覆盖）
        applyLevelQuotaIfAbsent(space);
        space.initUsage();
        // 非普通版仅管理员可创建
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !userAppService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        Object lock = lockMap.computeIfAbsent(userId, key -> new Object());
        synchronized (lock) {
            Long spaceId = transactionTemplate.execute(status -> {
                boolean exists = spaceRepository.existsByUserAndType(userId, space.getSpaceType());
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户最多只能创建一个空间");
                try {
                    spaceRepository.save(space);
                    // 团队空间：创建者默认是空间管理员，否则建完自己没有成员管理权限
                    if (space.isTeam()) {
                        SpaceUser creator = new SpaceUser();
                        creator.setSpaceId(space.getId());
                        creator.setUserId(userId);
                        creator.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                        spaceMemberRepository.save(creator);
                    }
                } finally {
                    lockMap.remove(userId);
                }
                return space.getId();
            });
            // 动态分表：新空间必须有自己的图片表 picture_{spaceId}
            spaceTableManager.ensureSpaceTable(spaceId);
            return spaceId;
        }
    }

    public Space getById(Long id) {
        return spaceRepository.findById(id);
    }

    public List<Space> listByIds(Collection<Long> ids) {
        return spaceRepository.findByIds(ids);
    }

    public PageResult<Space> page(SpaceQuery query) {
        return spaceRepository.page(query);
    }

    public List<Space> listTopByUsedSize(int topN) {
        return spaceRepository.listTopByUsedSize(topN);
    }

    public boolean update(Space space) {
        return spaceRepository.update(space);
    }

    public boolean removeById(Long id) {
        return spaceRepository.removeById(id);
    }

    /**
     * 原子增减用量（图片上传 / 删除时调用）
     */
    public boolean changeUsage(Long spaceId, long sizeDelta, long countDelta) {
        return spaceRepository.changeUsage(spaceId, sizeDelta, countDelta);
    }

    /**
     * 空间权限校验：创建者或平台管理员
     */
    public void checkSpaceAuth(User loginUser, Space space) {
        ThrowUtils.throwIf(loginUser == null || space == null, ErrorCode.NO_AUTH_ERROR);
        boolean isOwner = space.isOwner(loginUser.getId());
        if (!isOwner && !userAppService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    private void applyLevelQuotaIfAbsent(Space space) {
        SpaceLevelEnum level = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (level == null) {
            return;
        }
        if (space.getMaxSize() == null) {
            space.setMaxSize(level.getMaxSize());
        }
        if (space.getMaxCount() == null) {
            space.setMaxCount(level.getMaxCount());
        }
    }
}
