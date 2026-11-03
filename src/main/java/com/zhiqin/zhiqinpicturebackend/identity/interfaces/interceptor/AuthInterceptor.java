package com.zhiqin.zhiqinpicturebackend.identity.interfaces.interceptor;

import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserRoleEnum;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.annotation.AuthCheck;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@link AuthCheck} 的切面实现：校验当前登录用户是否具备注解要求的角色。
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserAppService userAppService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRoleValue = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userAppService.getLoginUser(request);
        UserRoleEnum mustRole = UserRoleEnum.findByValue(mustRoleValue);
        // 不需要权限，放行
        if (mustRole == null) {
            return joinPoint.proceed();
        }
        UserRoleEnum userRole = UserRoleEnum.findByValue(loginUser.getUserRole());
        if (userRole == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (UserRoleEnum.ADMIN.equals(mustRole) && !UserRoleEnum.ADMIN.equals(userRole)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }
}
