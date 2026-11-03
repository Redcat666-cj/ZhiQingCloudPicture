package com.zhiqin.zhiqinpicturebackend.identity.application.command;

import lombok.Data;

/**
 * 更新用户命令（管理员全量更新 / 用户自助更新都复用它，自助更新时 id 由登录态强制覆盖）
 */
@Data
public class UpdateUserCommand {

    private Long id;

    private String userName;

    private String userAvatar;

    private String userProfile;

    /**
     * 角色：user / admin（自助更新时不使用）
     */
    private String userRole;
}
