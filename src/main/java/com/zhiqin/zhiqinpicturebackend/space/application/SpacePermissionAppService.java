package com.zhiqin.zhiqinpicturebackend.space.application;

import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceRoleEnum;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceTypeEnum;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUser;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUserAuthConfig;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUserRole;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 空间权限应用服务：角色 → 权限码，以及"当前用户在某空间能做什么"。
 *
 * <p>权限码配置在 {@code biz/spaceAuthConfig.json}，启动时读一次缓存住。</p>
 *
 * <p>它替代了原来的 {@code manager.auth.SpacePermissionAppService}：逻辑没变，
 * 但依赖方向从"管理器反查两个 service"改成"应用服务依赖 space 领域模型 + 成员仓储"。</p>
 */
@Service
public class SpacePermissionAppService {

    private static final SpaceUserAuthConfig AUTH_CONFIG;

    static {
        String json = ResourceUtil.readUtf8Str("biz/spaceAuthConfig.json");
        AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    @Resource
    private SpaceMemberAppService spaceMemberAppService;

    @Resource
    private UserAppService userAppService;

    /**
     * 按角色取权限码
     */
    public List<String> getPermissions(String spaceUserRole) {
        if (StrUtil.isBlank(spaceUserRole) || AUTH_CONFIG == null || AUTH_CONFIG.getRoles() == null) {
            return new ArrayList<>();
        }
        SpaceUserRole role = AUTH_CONFIG.getRoles().stream()
                .filter(item -> spaceUserRole.equals(item.getKey()))
                .findFirst()
                .orElse(null);
        return role == null ? new ArrayList<>() : role.getPermissions();
    }

    /**
     * 当前用户在该空间拥有的权限码列表（前端据此控制按钮显隐）
     *
     * @param space     空间，null 表示公共图库
     * @param loginUser 登录用户，null 表示未登录
     */
    public List<String> getPermissionList(Space space, User loginUser) {
        if (loginUser == null) {
            return new ArrayList<>();
        }
        List<String> adminPermissions = getPermissions(SpaceRoleEnum.ADMIN.getValue());
        // 公共图库：仅平台管理员有全部权限
        if (space == null) {
            return userAppService.isAdmin(loginUser) ? adminPermissions : new ArrayList<>();
        }
        SpaceTypeEnum spaceType = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceType == null) {
            return new ArrayList<>();
        }
        switch (spaceType) {
            case PRIVATE:
                // 私有空间：本人或平台管理员
                return space.isOwner(loginUser.getId()) || userAppService.isAdmin(loginUser)
                        ? adminPermissions
                        : new ArrayList<>();
            case TEAM:
                SpaceUser spaceUser = spaceMemberAppService.findBySpaceAndUser(space.getId(), loginUser.getId());
                if (spaceUser == null) {
                    return Collections.emptyList();
                }
                return getPermissions(spaceUser.getSpaceRole());
            default:
                return new ArrayList<>();
        }
    }
}
