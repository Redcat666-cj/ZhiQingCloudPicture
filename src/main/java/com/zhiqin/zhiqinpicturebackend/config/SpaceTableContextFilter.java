package com.zhiqin.zhiqinpicturebackend.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.manager.table.PictureTableLocator;
import com.zhiqin.zhiqinpicturebackend.manager.table.SpaceTableContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 请求级分表上下文过滤器。
 *
 * <p>动态分表后，很多接口只拿得到一个图片 id（比如 {@code GET /picture/get/vo?id=xxx}），
 * 请求链路里的任意代码都可能直接 {@code pictureService.lambdaQuery().eq(id)} 去查图片
 * （现有的例子：Sa-Token 权限校验 StpInterfaceImpl#getPermissionList）。
 * 这类查询没有空间上下文，会默认落到 picture_public，于是「空间里的图片查不到」。</p>
 *
 * <p>这里在请求入口统一做一次 id → spaceId 的反查并放进 {@link SpaceTableContext}，
 * 之后同一请求内所有 picture 查询都会自动路由到正确的物理表，
 * 上层代码不用再关心「这条 SQL 该查哪张表」。</p>
 *
 * <p>顺序必须在 {@link HttpRequestWrapperFilter}（order=1）之后，
 * 这样才拿得到可重复读取的 request body。</p>
 */
@Slf4j
@Order(2)
@Component
public class SpaceTableContextFilter extends OncePerRequestFilter {

    private static final String PICTURE_MODULE = "picture";

    @Resource
    private PictureTableLocator pictureTableLocator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long pictureId = resolvePictureId(request);
        if (pictureId == null) {
            chain.doFilter(request, response);
            return;
        }
        // 反查这张图在哪个空间（公共图库返回 null）
        Long spaceId = pictureTableLocator.locateSpaceId(pictureId);
        Long previous = SpaceTableContext.get();
        SpaceTableContext.set(spaceId);
        try {
            chain.doFilter(request, response);
        } finally {
            SpaceTableContext.set(previous);
        }
    }

    /**
     * 只处理 /picture/** 的请求，并从中提取图片 id
     */
    private Long resolvePictureId(HttpServletRequest request) {
        try {
            String path = StrUtil.blankToDefault(request.getServletPath(), request.getRequestURI());
            if (!PICTURE_MODULE.equals(moduleOf(path))) {
                return null;
            }
            // 1. 查询参数 / 表单参数（GET /picture/get/vo?id=xxx 走这里）
            //    multipart 请求跳过：上传接口自己会把 spaceId 处理掉，避免提前触发文件解析
            String contentType = request.getContentType();
            if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
                return null;
            }
            Long id = parseLong(request.getParameter("id"));
            if (id != null) {
                return id;
            }
            // 2. JSON 请求体里的 id / pictureId（POST /picture/delete、out_painting/create_task 走这里）
            if (request instanceof RequestWrapper) {
                String body = ((RequestWrapper) request).getBody();
                if (StrUtil.isNotBlank(body) && JSONUtil.isTypeJSONObject(body)) {
                    JSONObject json = JSONUtil.parseObj(body);
                    id = parseLong(json.getStr("id"));
                    if (id == null) {
                        id = parseLong(json.getStr("pictureId"));
                    }
                    return id;
                }
            }
        } catch (Exception e) {
            // 解析失败不能影响业务，交给业务代码自己的参数校验
            log.debug("[动态分表] 解析请求中的图片 id 失败：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 取路径第一段：/picture/get/vo -> picture
     */
    private static String moduleOf(String path) {
        if (StrUtil.isBlank(path)) {
            return null;
        }
        String p = path.startsWith("/") ? path.substring(1) : path;
        int index = p.indexOf('/');
        return index < 0 ? p : p.substring(0, index);
    }

    private static Long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
