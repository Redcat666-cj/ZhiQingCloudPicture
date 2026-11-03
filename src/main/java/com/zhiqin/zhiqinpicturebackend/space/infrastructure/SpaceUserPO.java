package com.zhiqin.zhiqinpicturebackend.space.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间成员表持久化对象（space_user）。主键是数据库自增，注意 IdType.AUTO。
 */
@Data
@TableName(value = "space_user")
public class SpaceUserPO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spaceId;

    private Long userId;

    private String spaceRole;

    private Date createTime;

    private Date updateTime;
}
