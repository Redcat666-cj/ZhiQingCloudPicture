package com.zhiqin.zhiqinpicturebackend.space.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间成员聚合根（space_user）。
 *
 * <p>故意不塞进 {@link Space} 聚合：成员数量可能很大，且成员变更与空间本身的事务边界不同。</p>
 */
@Data
public class SpaceUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键（数据库自增）
     */
    private Long id;

    private Long spaceId;

    private Long userId;

    /**
     * 空间角色：viewer / editor / admin
     */
    private String spaceRole;

    private Date createTime;

    private Date updateTime;

    /**
     * 校验成员信息
     *
     * @param add 是否新建（新建时空间 id、用户 id 必填）
     */
    public void validate(boolean add) {
        if (add) {
            if (this.spaceId == null || this.userId == null) {
                throw new com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException(
                        com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode.PARAMS_ERROR);
            }
        }
        if (this.spaceRole != null && SpaceRoleEnum.getEnumByValue(this.spaceRole) == null) {
            throw new com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException(
                    com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }

    /**
     * 是否空间的创建者（团队成员里 role=admin 不代表是创建者，需按 id 判断）
     */
    public boolean isAdmin() {
        return SpaceRoleEnum.ADMIN.getValue().equals(this.spaceRole);
    }
}
