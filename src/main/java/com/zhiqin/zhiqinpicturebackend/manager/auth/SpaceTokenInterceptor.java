package com.zhiqin.zhiqinpicturebackend.manager.auth;

import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static com.zhiqin.zhiqinpicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 空间账号体系(StpKit.SPACE)登录态兜底拦截器
 *
 * <p>sa-token 的会话默认保存在内存中，服务重启后 StpKit.SPACE 的登录态会丢失，
 * 而用户登录态保存在 Redis（spring-session）里；此时会出现
 * 「用户明明登录了，空间相关接口却返回 40100 未能读取到有效 token」。
 * 这里在进入 Controller 前检查一次：用户已登录但空间账号体系未登录时，补建一次登录态。</p>
 *
 * <p>必须排在 SaInterceptor 之前（order 更小），否则权限注解会先执行并直接拒绝请求。</p>
 */
@Component
public class SpaceTokenInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return true;
        }
        Object attribute = session.getAttribute(USER_LOGIN_STATE);
        if (!(attribute instanceof User)) {
            return true;
        }
        User loginUser = (User) attribute;
        if (loginUser.getId() == null || StpKit.SPACE.isLogin()) {
            return true;
        }
        StpKit.SPACE.login(loginUser.getId());
        StpKit.SPACE.getSessionByLoginId(loginUser.getId()).set(USER_LOGIN_STATE, loginUser);
        return true;
    }
}
