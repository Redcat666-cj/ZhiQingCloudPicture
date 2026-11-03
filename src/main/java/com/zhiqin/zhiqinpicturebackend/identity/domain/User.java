package com.zhiqin.zhiqinpicturebackend.identity.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户聚合根。
 *
 * <p>纯领域对象：不依赖 Spring、不依赖 MyBatis-Plus，
 * 持久化注解全部挪到了 {@code identity.infrastructure.UserPO}。</p>
 *
 * <p>聚合内行为集中在这里（当前只有角色判断），
 * 后续「改昵称/改头像」等规则也应长在这个类上，而不是散落在 service。</p>
 */
@Data
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码（密文）
     */
    private String userPassword;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user / admin
     */
    private String userRole;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    private Integer isDelete;

    /**
     * 是否管理员
     */
    public boolean isAdmin() {
        return UserRoleEnum.ADMIN.getValue().equals(this.userRole);
    }

    /**
     * 是否已被逻辑删除
     */
    public boolean isDeleted() {
        return this.isDelete != null && this.isDelete == 1;
    }

    /**
     * 是否为游客/非法账号（id 缺失）
     */
    public boolean isTransient() {
        return this.id == null || this.id <= 0;
    }
}
