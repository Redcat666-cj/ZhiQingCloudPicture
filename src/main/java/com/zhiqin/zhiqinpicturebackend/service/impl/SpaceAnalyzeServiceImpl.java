package com.zhiqin.zhiqinpicturebackend.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiqin.zhiqinpicturebackend.domain.dto.*;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Picture;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Space;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.domain.vo.*;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.exception.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.manager.table.SpaceTableContext;
import com.zhiqin.zhiqinpicturebackend.manager.table.SpaceTableManager;
import com.zhiqin.zhiqinpicturebackend.service.PictureService;
import com.zhiqin.zhiqinpicturebackend.service.SpaceAnalyzeService;
import com.zhiqin.zhiqinpicturebackend.service.SpaceService;
import com.zhiqin.zhiqinpicturebackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Service
public class SpaceAnalyzeServiceImpl implements SpaceAnalyzeService {
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private PictureService pictureService;
    @Resource
    private SpaceTableManager spaceTableManager;

    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {


        //校验权限

        if (spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic()) {
            // 全空间分析或者公共图库权限校验：仅管理员可访问
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.PARAMS_ERROR, "无权访问公共图库");

        } else {
            //私有空间权限校验
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null || space.getId() <= 0, ErrorCode.PARAMS_ERROR);
            spaceService.checkSpaceAuth(loginUser, space);
        }
    }

    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        if (spaceAnalyzeRequest.isQueryAll()) {
            return;
        }
        if (spaceAnalyzeRequest.isQueryPublic()) {
            queryWrapper.isNull("spaceId");
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");


    }

    /**
     * 获取空间使用分析数据
     *
     * @param spaceUsageAnalyzeRequest SpaceUsageAnalyzeRequest 请求参数
     * @param loginUser                当前登录用户
     * @return SpaceUsageAnalyzeResponse 分析结果
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            // 查询全部或公共图库逻辑
            // 仅管理员可以访问
            boolean admin = userService.isAdmin(loginUser);
            ThrowUtils.throwIf(!admin, ErrorCode.NO_AUTH_ERROR);
            //统计公共图库 / 全部图库资源的使用
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize");
            long size;
            long count;
            if (spaceUsageAnalyzeRequest.isQueryAll()) {
                // 跨空间：物理表是按空间拆的，逐张表统计再汇总
                size = 0L;
                count = 0L;
                for (Long scope : allScopes()) {
                    List<Object> oneTable = SpaceTableContext.call(scope,
                            () -> pictureService.getBaseMapper().selectObjs(queryWrapper));
                    size += oneTable.stream().mapToLong(result -> result instanceof Long ? (Long) result : 0).sum();
                    count += oneTable.size();
                }
            } else {
                // 公共图库：路由到 picture_public
                queryWrapper.isNull("spaceId");
                List<Object> pictureObjList = SpaceTableContext.call(null,
                        () -> pictureService.getBaseMapper().selectObjs(queryWrapper));
                size = pictureObjList.stream().mapToLong(result -> result instanceof Long ? (Long) result : 0).sum();
                count = pictureObjList.size();
            }
            //封装返回结果
            // 公共图库无上限、无比例
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(size);
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setUsedCount(count);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;


        } else {
            //查询指定空间
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            //获取空间信息
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null || space.getId() <= 0, ErrorCode.PARAMS_ERROR);
            //权限校验
            spaceService.checkSpaceAuth(loginUser, space);
            //构造返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            long usedSize = space.getTotalSize() == null ? 0 : space.getTotalSize();
            long maxSize = space.getMaxSize() == null ? 0 : space.getMaxSize();
            long usedCount = space.getTotalCount() == null ? 0 : space.getTotalCount();
            long maxCount = space.getMaxCount() == null ? 0 : space.getMaxCount();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            spaceUsageAnalyzeResponse.setMaxSize(maxSize);
            // 后端直接算好百分比，这样前端可以直接展示
            double sizeUsageRatio = maxSize <= 0 ? 0 : NumberUtil.round(usedSize * 100.0 / maxSize, 2).doubleValue();
            spaceUsageAnalyzeResponse.setSizeUsageRatio(sizeUsageRatio);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
            spaceUsageAnalyzeResponse.setMaxCount(maxCount);
            double countUsageRatio = maxCount <= 0 ? 0 : NumberUtil.round(usedCount * 100.0 / maxCount, 2).doubleValue();
            spaceUsageAnalyzeResponse.setCountUsageRatio(countUsageRatio);
            return spaceUsageAnalyzeResponse;

        }

    }

    /**
     * 空间图片分类分析
     *
     * @param request
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        //校验权限
        checkSpaceAnalyzeAuth(request, loginUser);
        //构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        //根据分析范围补充查询条件
        fillAnalyzeQueryWrapper(request, queryWrapper);
        //使用mybatisplus分组查询
        queryWrapper.select("category AS category", "COUNT(*) AS count",
                "SUM(picSize) AS totalSize").groupBy("category");
        //查询并转换结果（跨空间时同一个分类会出现多次，按分类合并）
        Map<String, long[]> merged = new LinkedHashMap<>();
        for (Map<String, Object> result : selectMapsAcrossScope(queryWrapper, request)) {
            String category = result.get("category") != null ? result.get("category").toString() : "未分类";
            long count = result.get("count") == null ? 0 : ((Number) result.get("count")).longValue();
            long totalSize = result.get("totalSize") == null ? 0 : ((Number) result.get("totalSize")).longValue();
            long[] cell = merged.computeIfAbsent(category, key -> new long[2]);
            cell[0] += count;
            cell[1] += totalSize;
        }
        return merged.entrySet().stream()
                .map(entry -> new SpaceCategoryAnalyzeResponse(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .collect(Collectors.toList());
    }

    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 检查权限
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);
        //查询所有符合条件的标签
        queryWrapper.select("tags");
        List<String> tagsJsonList = selectObjsAcrossScope(queryWrapper, spaceTagAnalyzeRequest)
                .stream()
                .filter(ObjectUtil::isNotNull)
                .map(Object::toString)
                .collect(Collectors.toList());
        //合并所有标签并统计使用次数
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                .filter(tagsJson -> tagsJson.startsWith("["))
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        //转换为响应对象，按使用降低排序
        return tagCountMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))//降序排列
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 检查权限
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);

        // 查询所有符合条件的图片大小
        queryWrapper.select("picSize");
        List<Long> picSizes = selectObjsAcrossScope(queryWrapper, spaceSizeAnalyzeRequest)
                .stream()
                .map(size -> ((Number) size).longValue())
                .collect(Collectors.toList());
        //定义分段范围，注意使用有序的Map
        Map<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizes.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizes.stream().filter(size -> size > 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizes.stream().filter(size -> size >= 500 * 1024 && size < 1 * 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizes.stream().filter(size -> size >= 1 * 1024 * 1024).count());
        // 转换为响应对象
        return sizeRanges.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 检查权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);
        //分析维度：每日。每周，每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension) {
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime,'%Y-%m-%d')AS period", "COUNT(*) AS count");
                break;
            case "week":
                queryWrapper.select("YEARWEEK(createTime) AS period", "COUNT(*) AS count");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') AS period", "COUNT(*) AS count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }
        //分组和排序
        queryWrapper.groupBy("period").orderByAsc("period");

        //查询结果并转换（跨空间时同一个时间段会出现多次，按 period 累加）
        Map<String, Long> merged = new LinkedHashMap<>();
        for (Map<String, Object> result : selectMapsAcrossScope(queryWrapper, spaceUserAnalyzeRequest)) {
            String period = String.valueOf(result.get("period"));
            long count = ((Number) result.get("count")).longValue();
            merged.merge(period, count, Long::sum);
        }
        return merged.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SpaceUserAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

    }

    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 仅管理员可查看空间排行
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看空间排行");
        // 构造查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("LIMIT " + spaceRankAnalyzeRequest.getTopN()); // 取前 N 名
        return spaceService.list(queryWrapper);
    }

    // ==================== 动态分表：分析查询的表范围 ====================

    /**
     * 所有图片表的范围：null（公共图库 picture_public）+ 每个空间（picture_{spaceId}）
     */
    private List<Long> allScopes() {
        List<Long> scopes = new ArrayList<>();
        scopes.add(null);
        scopes.addAll(spaceTableManager.listSpaceIds());
        return scopes;
    }

    /**
     * 本次分析要查的空间：queryPublic -> null（公共图库），否则用请求里的 spaceId
     */
    private Long resolveScope(SpaceAnalyzeRequest request) {
        return request.isQueryPublic() ? null : request.getSpaceId();
    }

    /**
     * 按分析范围查单列数据：queryAll 需要把所有空间表扫一遍再拼接
     */
    private List<Object> selectObjsAcrossScope(QueryWrapper<Picture> queryWrapper, SpaceAnalyzeRequest request) {
        if (!request.isQueryAll()) {
            return SpaceTableContext.call(resolveScope(request),
                    () -> pictureService.getBaseMapper().selectObjs(queryWrapper));
        }
        List<Object> result = new ArrayList<>();
        for (Long scope : allScopes()) {
            result.addAll(SpaceTableContext.call(scope,
                    () -> pictureService.getBaseMapper().selectObjs(queryWrapper)));
        }
        return result;
    }

    /**
     * 按分析范围查 Map 结果：queryAll 需要把所有空间表扫一遍再拼接
     */
    private List<Map<String, Object>> selectMapsAcrossScope(QueryWrapper<Picture> queryWrapper, SpaceAnalyzeRequest request) {
        if (!request.isQueryAll()) {
            return SpaceTableContext.call(resolveScope(request),
                    () -> pictureService.getBaseMapper().selectMaps(queryWrapper));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long scope : allScopes()) {
            result.addAll(SpaceTableContext.call(scope,
                    () -> pictureService.getBaseMapper().selectMaps(queryWrapper)));
        }
        return result;
    }
}
