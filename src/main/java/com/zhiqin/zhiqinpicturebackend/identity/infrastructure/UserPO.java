package com.zhiqin.zhiqinpicturebackend.identity.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户表持久化对象（user）。
 *
 * <p>MyBatis-Plus 的注解只允许出现在这里，领域模型 {@code identity.domain.User} 保持纯净。</p>
 */
@Data
@TableName(value = "user")
public class UserPO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String userAccount;

    private String userPassword;

    private String userName;

    private String userAvatar;

    private String userProfile;

    private String userRole;

    private Date editTime;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;
}
