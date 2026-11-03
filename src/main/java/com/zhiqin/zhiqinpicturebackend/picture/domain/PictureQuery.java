package com.zhiqin.zhiqinpicturebackend.picture.domain;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 图片查询条件（领域查询对象）。
 *
 * <p>把原来 {@code PictureAppServiceImpl#getQueryWrapper} 里的字段收成数据对象，
 * 由基础设施层翻译成 SQL；跨空间查询用 {@link #crossSpaces} 表达。</p>
 */
@Data
public class PictureQuery {

    private Long id;

    private String name;

    private String introduction;

    private String category;

    private List<String> tags;

    private Long picSize;

    private Integer picWidth;

    private Integer picHeight;

    private Double picScale;

    private String picFormat;

    /**
     * 关键词：同时模糊匹配名称与简介
     */
    private String searchText;

    private Long userId;

    /**
     * 空间 id；null 且 nonNullSpaceId=false 时表示公共图库
     */
    private Long spaceId;

    /**
     * 是否只查公共图库（space_id is null）
     */
    private boolean nullSpaceId;

    private Integer reviewStatus;

    private String reviewMessage;

    private Long reviewerId;

    private Date startEditTime;

    private Date endEditTime;

    /**
     * 是否跨所有空间表查询（管理员全局列表用）
     */
    private boolean crossSpaces;

    private String sortField;

    private String sortOrder = "descend";

    private long current = 1;

    private long size = 10;

    public boolean isAscend() {
        return "ascend".equalsIgnoreCase(sortOrder);
    }
}
