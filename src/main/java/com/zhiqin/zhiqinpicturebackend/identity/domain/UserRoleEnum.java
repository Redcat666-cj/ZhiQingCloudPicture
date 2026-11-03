package com.zhiqin.zhiqinpicturebackend.identity.domain;

import lombok.Getter;

/**
 * 用户角色（领域枚举，纯 JDK 实现，不依赖工具库）。
 */
@Getter
public enum UserRoleEnum {

    USER("用户", "user"),
    ADMIN("管理员", "admin");

    private final String text;

    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据存储值反查枚举，找不到返回 null
     */
    public static UserRoleEnum findByValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (UserRoleEnum roleEnum : values()) {
            if (roleEnum.getValue().equals(value)) {
                return roleEnum;
            }
        }
        return null;
    }
}
