package com.zhiqin.zhiqinpicturebackend.aop;

import com.zhiqin.zhiqinpicturebackend.annotation.AuthCheck;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.domain.enums.UserRoleEnum;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.condition.RequestConditionHolder;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {
    /*权限校验*/
    @Resource
    private UserService userService;
    @Around("@annotation(authCheck)")
    public Object  doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {

        String result = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        //获取当前用户
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRole = UserRoleEnum.findByValue(result);
        //如果不需要权限放行
        if(mustRole == null){
            return joinPoint.proceed();
        }
        //必须有权限才通过
        UserRoleEnum userRoleEnum = UserRoleEnum.findByValue(loginUser.getUserRole());
        if(userRoleEnum == null){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if(userRoleEnum.ADMIN.equals(mustRole)&& !UserRoleEnum.ADMIN.equals(userRoleEnum)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }

}
