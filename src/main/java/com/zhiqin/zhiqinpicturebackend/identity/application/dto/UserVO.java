package com.zhiqin.zhiqinpicturebackend.identity.application.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户视图对象（对外输出模型，不含密码等敏感字段）。
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String userAccount;

    private String userName;

    private String userAvatar;

    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    private Date createTime;
}
