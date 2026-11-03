package com.zhiqin.zhiqinpicturebackend.space.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间表持久化对象（space）。
 */
@Data
@TableName(value = "space")
public class SpacePO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String spaceName;

    private Integer spaceLevel;

    private Long maxSize;

    private Long maxCount;

    private Long totalSize;

    private Long totalCount;

    private Long userId;

    private Date createTime;

    private Date editTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    private Integer spaceType;
}
