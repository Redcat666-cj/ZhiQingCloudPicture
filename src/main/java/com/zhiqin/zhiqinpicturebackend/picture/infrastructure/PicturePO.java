package com.zhiqin.zhiqinpicturebackend.picture.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片表持久化对象（逻辑表 picture，运行期由动态表名插件改写到 picture_public / picture_{spaceId}）。
 */
@Data
@TableName(value = "picture")
public class PicturePO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String url;

    private Long spaceId;

    private String thumbnailUrl;

    private String name;

    private String introduction;

    private String category;

    private String tags;

    private Long picSize;

    private Integer picWidth;

    private Integer picHeight;

    private Double picScale;

    private String picFormat;

    private Long userId;

    private Date createTime;

    private Date editTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    private Integer reviewStatus;

    private String reviewMessage;

    private Long reviewerId;

    private Date reviewTime;

    private String picColor;
}
