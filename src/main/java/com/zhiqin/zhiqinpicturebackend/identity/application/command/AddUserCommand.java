package com.zhiqin.zhiqinpicturebackend.identity.application.command;

import lombok.Data;

/**
 * 管理员新增用户命令
 */
@Data
public class AddUserCommand {

    private String userAccount;

    private String userName;

    private String userAvatar;

    private String userProfile;

    /**
     * 角色：user / admin
     */
    private String userRole;
}
