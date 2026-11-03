package com.zhiqin.zhiqinpicturebackend.identity.application.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册命令
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCommand {

    /**
     * 账号
     */
    private String account;

    /**
     * 明文密码
     */
    private String password;

    /**
     * 确认密码
     */
    private String confirmPassword;
}
