package com.zhiqin.zhiqinpicturebackend.identity.application.dto;

import lombok.Data;

import java.util.Date;

/**
 * 登录用户视图对象（前端登录态展示用）。
 */
@Data
public class LoginUserVo {

    private Long id;

    private String userAccount;

    private String userName;

    private String userAvatar;

    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    private Date editTime;

    private Date createTime;

    private Date updateTime;
}
