package com.zhiqin.zhiqinpicturebackend.domain.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

@Getter
public enum PictureReviewStatusEnum {
    REVIEWING("待审核", 0),
    PASS("通过", 1),
    REJECT("拒绝", 2);


    private final String text;

    private final int value;

    PictureReviewStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }
    public static PictureReviewStatusEnum findByValue(int value) {

        //后期枚举非常多查找可以用map存
        if(ObjectUtil.isNotEmpty(value)){
            for (PictureReviewStatusEnum pictureReviewStatusEnum : PictureReviewStatusEnum.values()) {
                if (pictureReviewStatusEnum.value == value) {
                    return pictureReviewStatusEnum;
                }
            }
        }
        return null;
    }
}
