package com.zhiqin.zhiqinpicturebackend.identity.interfaces.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解：标注在接口方法上，由 {@link com.zhiqin.zhiqinpicturebackend.identity.interfaces.interceptor.AuthInterceptor} 拦截。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须具备的角色，空字符串表示不校验
     */
    String mustRole() default "";
}
