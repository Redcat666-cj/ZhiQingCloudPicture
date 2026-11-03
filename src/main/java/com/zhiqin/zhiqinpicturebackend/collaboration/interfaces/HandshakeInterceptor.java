package com.zhiqin.zhiqinpicturebackend.collaboration.interfaces;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.zhiqin.zhiqinpicturebackend.picture.domain.Picture;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceTypeEnum;
import com.zhiqin.zhiqinpicturebackend.space.application.SpacePermissionAppService;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUserPermissionConstant;
import com.zhiqin.zhiqinpicturebackend.picture.infrastructure.sharding.PictureTableLocator;
import com.zhiqin.zhiqinpicturebackend.picture.infrastructure.sharding.SpaceTableContext;
import com.zhiqin.zhiqinpicturebackend.picture.application.PictureAppService;
import com.zhiqin.zhiqinpicturebackend.space.application.SpaceAppService;
import com.zhiqin.zhiqinpicturebackend.space.application.SpaceMemberAppService;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.N;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class HandshakeInterceptor implements org.springframework.web.socket.server.HandshakeInterceptor {

    @Resource
    private UserAppService UserAppService;

    @Resource
    private SpaceAppService SpaceAppService;

    @Resource
    private PictureAppService PictureAppService;

    @Resource
    private SpacePermissionAppService SpacePermissionAppService;

    @Resource
    private PictureTableLocator pictureTableLocator;


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
            User loginUser = UserAppService.getLoginUser(servletRequest);
            if(ObjUtil.isEmpty(loginUser)){
                log.error("用户未登录拒绝握手");
                return false;
            }
            // 校验用户是否有该图片的权限
            // 动态分表：这里不是 /picture/** 请求，SpaceTableContextFilter 不会帮忙定位物理表，
            // 所以先用 pictureId 反查它属于哪个空间，再查图片，否则「空间里的图片」会被误判成不存在
            Long pictureIdLong = Long.valueOf(pictureId);
            Long locatedSpaceId = pictureTableLocator.locateSpaceId(pictureIdLong);
            Long previousSpaceId = SpaceTableContext.get();
            SpaceTableContext.set(locatedSpaceId);
            Picture picture;
            try {
                picture = PictureAppService.getById(pictureIdLong);
            } finally {
                SpaceTableContext.set(previousSpaceId);
            }
            if(picture==null){
                log.error("图片不存在拒绝握手");
                return false;
            }
            Long spaceId = picture.getSpaceId();
            Space space = null;
            if(spaceId!=null){
                space = SpaceAppService.getById(spaceId);
                if(space==null){
                    log.error("图片所属空间不存在拒绝握手");
                    return false;
                }
                // Integer 与 int 比较会自动拆箱，这里用 equals 避免空指针
                if(!Integer.valueOf(SpaceTypeEnum.TEAM.getValue()).equals(space.getSpaceType())){
                    log.error("图片所属空间不是团队空间拒绝握手");
                    return false;
                }


            }
            List<String> permissionList = SpacePermissionAppService.getPermissionList(space, loginUser);
            if(!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)){
                log.error("没有图片编辑权限");
                return false;
            }
            //设置attributes
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            attributes.put("spaceId", spaceId);
            attributes.put("pictureId", pictureIdLong);//前端js不能处理long类型，所以传给前端的是string类型，后端需要转为long

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
