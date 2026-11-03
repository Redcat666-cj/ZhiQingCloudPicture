package com.zhiqin.zhiqinpicturebackend.space.domain;

import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间聚合根。
 *
 * <p>把原先散在 service 里的规则收进来：</p>
 * <ul>
 *   <li>配额判断（还能不能再放一张图）→ {@link #ensureCanHold(long)}</li>
 *   <li>按空间级别填充默认额度 → {@link #applyLevelQuota()}</li>
 *   <li>归属/类型判断 → {@link #isOwner(Long)} / {@link #isTeam()}</li>
 * </ul>
 *
 * <p>纯领域对象：不含 MyBatis-Plus 注解，持久化由 {@code space.infrastructure.SpacePO} 负责。</p>
 */
@Data
public class Space implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 最大容量（字节）
     */
    private Long maxSize;

    /**
     * 最大条数
     */
    private Long maxCount;

    /**
     * 已用容量（字节）
     */
    private Long totalSize;

    /**
     * 已用条数
     */
    private Long totalCount;

    /**
     * 创建者 id
     */
    private Long userId;

    private Date createTime;

    private Date editTime;

    private Date updateTime;

    private Integer isDelete;

    /**
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;

    /**
     * 是否为该空间的创建者
     */
    public boolean isOwner(Long loginUserId) {
        return loginUserId != null && loginUserId.equals(this.userId);
    }

    /**
     * 是否团队空间
     */
    public boolean isTeam() {
        return SpaceTypeEnum.TEAM.getValue() == (this.spaceType == null ? SpaceTypeEnum.PRIVATE.getValue() : this.spaceType);
    }

    /**
     * 是否私有空间
     */
    public boolean isPrivate() {
        return !isTeam();
    }

    /**
     * 当前配额快照
     */
    public SpaceQuota quota() {
        return new SpaceQuota(maxSize, maxCount, totalSize, totalCount);
    }

    /**
     * 校验还能再放一张 size 字节的图片，超限直接抛业务异常
     */
    public void ensureCanHold(long size) {
        quota().ensureCanHold(size);
    }

    /**
     * 按空间级别填充默认额度（创建空间时用）
     */
    public void applyLevelQuota() {
        SpaceLevelEnum level = SpaceLevelEnum.getEnumByValue(this.spaceLevel);
        if (level == null) {
            return;
        }
        this.maxSize = level.getMaxSize();
        this.maxCount = level.getMaxCount();
    }

    /**
     * 初始化额度计数（新建空间时）
     */
    public void initUsage() {
        if (this.totalSize == null) {
            this.totalSize = 0L;
        }
        if (this.totalCount == null) {
            this.totalCount = 0L;
        }
    }

    /**
     * 校验空间名称/级别/类型是否合法
     *
     * @param add 是否为创建场景（创建时更多字段必填）
     */
    public void validate(boolean add) {
        if (add) {
            if (this.spaceName == null || this.spaceName.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (this.spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            if (SpaceTypeEnum.getEnumByValue(this.spaceType) == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类别不能为空");
            }
        }
        if (this.spaceLevel != null && SpaceLevelEnum.getEnumByValue(this.spaceLevel) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (this.spaceName != null && this.spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
        if (this.spaceType != null && SpaceTypeEnum.getEnumByValue(this.spaceType) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不存在");
        }
    }
}
