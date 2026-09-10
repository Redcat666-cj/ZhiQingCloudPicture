package com.zhiqin.zhiqinpicturebackend.domain.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

@Getter
public enum UserRoleEnum {
    USER("用户","user"),
    ADMIN("管理员","admin");


    private final String text;

    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }
    public static UserRoleEnum findByValue(String value) {

        //后期枚举非常多查找可以用map存
        if(ObjectUtil.isNotEmpty(value)){
            for (UserRoleEnum roleEnum : UserRoleEnum.values()) {
                if (roleEnum.getValue().equals(value)) {
                    return roleEnum;
                }
            }
        }
        return null;
    }
}
