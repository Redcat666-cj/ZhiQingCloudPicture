package com.zhiqin.zhiqinpicturebackend.space.domain;

import lombok.Data;

/**
 * 空间查询条件（领域查询对象，避免应用层接触 QueryWrapper）。
 */
@Data
public class SpaceQuery {

    private Long id;

    private Long userId;

    private String spaceName;

    private Integer spaceLevel;

    private Integer spaceType;

    private String sortField;

    private String sortOrder = "descend";

    private long current = 1;

    private long size = 10;

    public boolean isAscend() {
        return "ascend".equalsIgnoreCase(sortOrder);
    }
}
