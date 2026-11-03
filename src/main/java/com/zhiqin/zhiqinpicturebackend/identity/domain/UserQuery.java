package com.zhiqin.zhiqinpicturebackend.identity.domain;

import lombok.Data;

/**
 * 用户查询条件（领域侧的查询对象）。
 *
 * <p>它的存在是为了让仓储接口不暴露 MyBatis-Plus 的 {@code QueryWrapper}：
 * 应用层描述"想查什么"，基础设施层负责翻译成具体 SQL。</p>
 */
@Data
public class UserQuery {

    private Long id;

    private String userAccount;

    private String userName;

    private String userProfile;

    private String userRole;

    /**
     * 排序字段（null 表示不排序）
     */
    private String sortField;

    /**
     * 排序方向：ascend / descend
     */
    private String sortOrder = "descend";

    /**
     * 页码，从 1 开始
     */
    private long current = 1;

    /**
     * 每页条数
     */
    private long size = 10;

    public boolean isAscend() {
        return "ascend".equalsIgnoreCase(sortOrder);
    }
}
