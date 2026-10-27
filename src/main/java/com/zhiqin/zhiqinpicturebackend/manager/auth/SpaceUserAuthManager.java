package com.zhiqin.zhiqinpicturebackend.manager.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Space;
import com.zhiqin.zhiqinpicturebackend.domain.entity.SpaceUser;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.domain.enums.SpaceRoleEnum;
import com.zhiqin.zhiqinpicturebackend.domain.enums.SpaceTypeEnum;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.manager.auth.model.SpaceUserAuthConfig;
import com.zhiqin.zhiqinpicturebackend.manager.auth.model.SpaceUserRole;
import com.zhiqin.zhiqinpicturebackend.service.SpaceUserService;
import com.zhiqin.zhiqinpicturebackend.service.UserService;
import io.netty.util.internal.ResourcesUtil;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpaceUserAuthManager {

    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private UserService userService;


    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;
    static {
        String json = ResourceUtil.readUtf8Str("biz/spaceAuthConfig.json");
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    /*
    * 根据用户获取权限列表
    *
    * */
    public List<String> getPermissions(String spaceUserRole) {
        if(StringUtils.isEmpty(spaceUserRole)){
           return new ArrayList<>();
    }
        //找到匹配的角色
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles().stream()
                .filter(r -> spaceUserRole.equals(r.getKey()))
                .findFirst()
                .orElse(null);
        if(role == null){
        return new ArrayList<>();
        }
        return role.getPermissions();
    }

    public List<String> getPermissionList(Space space, User loginUser) {
        if (loginUser == null) {
            return new ArrayList<>();
        }
        // 管理员权限
        List<String> ADMIN_PERMISSIONS = getPermissions(SpaceRoleEnum.ADMIN.getValue());
        // 公共图库
        if (space == null) {
            if (userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            return new ArrayList<>();
        }
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 根据空间获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限
                SpaceUser spaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, loginUser.getId())
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    return getPermissions(spaceUser.getSpaceRole());
                }
        }
        return new ArrayList<>();
    }


}
