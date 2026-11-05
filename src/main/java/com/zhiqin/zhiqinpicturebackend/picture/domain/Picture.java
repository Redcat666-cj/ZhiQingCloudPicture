package com.zhiqin.zhiqinpicturebackend.picture.domain;

import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片聚合根。
 *
 * <p>纯领域对象（无 MyBatis 注解）。审核状态机、归属判断、软删除这些规则都长在这里，
 * 原来散在 {@code PictureAppServiceImpl#fillReviewParams / doPictureReview} 里。</p>
 */
@Data
public class Picture implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String url;

    /**
     * 空间 id；null 表示公共图库
     */
    private Long spaceId;

    private String thumbnailUrl;

    private String name;

    private String introduction;

    private String category;

    /**
     * 标签（JSON 数组字符串）
     */
    private String tags;

    private Long picSize;

    private Integer picWidth;

    private Integer picHeight;

    private Double picScale;

    private String picFormat;

    /**
     * 上传用户 id
     */
    private Long userId;

    private Date createTime;

    private Date editTime;

    private Date updateTime;

    private Integer isDelete;

    /**
     * 审核状态：0-待审核 1-通过 2-拒绝
     */
    private Integer reviewStatus;

    private String reviewMessage;

    private Long reviewerId;

    private Date reviewTime;

    /**
     * 主色调
     */
    private String picColor;

    /**
     * 浏览数（详情页每打开一次 +1）
     */
    private Long reviews;

    /**
     * 最近一次浏览时间（reviews 最后一次变化的时间），冷数据降沉的判断依据
     */
    private Date lastViewTime;

    /**
     * 是否已降沉至低频存储：0-否 1-是
     */
    private Integer demoted;

    /**
     * 是否已降沉至低频存储
     */
    public boolean isDemoted() {
        return this.demoted != null && this.demoted == 1;
    }

    /**
     * 是否公共图库图片
     */
    public boolean isPublic() {
        return this.spaceId == null;
    }

    /**
     * 是否上传者本人
     */
    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.userId);
    }

    /**
     * 是否已被逻辑删除
     */
    public boolean isDeleted() {
        return this.isDelete != null && this.isDelete == 1;
    }

    /**
     * 当前审核状态
     */
    public PictureReviewStatusEnum reviewStatus() {
        return this.reviewStatus == null ? PictureReviewStatusEnum.REVIEWING
                : PictureReviewStatusEnum.findByValue(this.reviewStatus);
    }

    /**
     * 审核通过/拒绝：状态机在这里，重复审核直接抛错
     */
    public void review(PictureReviewStatusEnum target, Long reviewerId, String message, Date reviewTime) {
        if (target == null || target == PictureReviewStatusEnum.REVIEWING) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不合法");
        }
        if (target == reviewStatus()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        this.reviewStatus = target.getValue();
        this.reviewerId = reviewerId;
        this.reviewMessage = message;
        this.reviewTime = reviewTime;
    }

    /**
     * 上传后的默认审核状态：管理员直接过审，普通用户待审
     */
    public void initReviewStatus(boolean uploaderIsAdmin, Long uploaderId, Date now) {
        if (uploaderIsAdmin) {
            this.reviewStatus = PictureReviewStatusEnum.PASS.getValue();
            this.reviewerId = uploaderId;
            this.reviewMessage = "管理员自动过审";
            this.reviewTime = now;
        } else {
            this.reviewStatus = PictureReviewStatusEnum.REVIEWING.getValue();
        }
    }

    /**
     * 软删除
     */
    public void softDelete() {
        this.isDelete = 1;
    }
}
