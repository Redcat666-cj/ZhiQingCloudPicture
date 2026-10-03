package com.zhiqin.zhiqinpicturebackend.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class PictureReviewRequest implements Serializable {
  
    /**  
     * id  
     */  
    private Long id;

    /**
     * 审核状态：0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 审核人 ID
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private Date reviewTime;
}
