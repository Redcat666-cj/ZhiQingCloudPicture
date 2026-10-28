package com.zhiqin.zhiqinpicturebackend.manager.websocket;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Picture;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Space;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.domain.enums.SpaceTypeEnum;
import com.zhiqin.zhiqinpicturebackend.manager.auth.SpaceUserAuthManager;
import com.zhiqin.zhiqinpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.zhiqin.zhiqinpicturebackend.service.PictureService;
import com.zhiqin.zhiqinpicturebackend.service.SpaceService;
import com.zhiqin.zhiqinpicturebackend.service.SpaceUserService;
import com.zhiqin.zhiqinpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.N;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;

import javax.annotation.Resource;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class HandshakeInterceptor implements org.springframework.web.socket.server.HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;


    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        // 在这里可以进行握手前的处理，比如验证用户身份、设置属性等
        log.info("WebSocket 握手开始: {}", request.getURI());
        if(request instanceof ServletServerHttpRequest){
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            //获取请求参数
            String pictureId = servletRequest.getParameter("pictureId");
            if(StrUtil.isBlank(pictureId)){
                log.error("缺少图片参数,拒绝握手");
                return false;
            }
            User loginUser = userService.getLoginUser(servletRequest);
            if(ObjUtil.isEmpty(loginUser)){
                log.error("用户未登录拒绝握手");
                return false;
            }
            // 校验用户是否有该图片的权限
            Picture picture = pictureService.getById(pictureId);
            if(picture==null){
                log.error("图片不存在拒绝握手");
                return false;
            }
            Long spaceId = picture.getSpaceId();
            Space space = null;
            if(spaceId!=null){
                space = spaceService.getById(spaceId);
                if(space==null){
                    log.error("图片所属空间不存在拒绝握手");
                    return false;
                }
                if(space.getSpaceType()!= SpaceTypeEnum.TEAM.getValue()){
                    log.error("图片所属空间不是团队空间拒绝握手");
                    return false;
                }


            }
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            if(!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)){
                log.error("没有图片编辑权限");
                return false;
            }
            //设置attributes
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            attributes.put("pictureId",Long.valueOf(pictureId));//前端js不能处理long类型，所以传给前端的是string类型，后端需要转为long

        }
        // 例如，可以从请求中获取参数并存储在 attributes 中，以便在 WebSocketHandler 中使用
        // attributes.put("userId", userId);
        return true; // 返回 true 表示允许握手，返回 false 则拒绝握手



    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, @Nullable Exception exception) {
        // 握手完成后的处理
        log.info("WebSocket 握手完成: {}", request.getURI());
    }
}
