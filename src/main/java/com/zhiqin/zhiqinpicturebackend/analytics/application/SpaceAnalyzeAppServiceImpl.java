package com.zhiqin.zhiqinpicturebackend.analytics.application;

import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.dto.SpaceAnalyzeRequest;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.dto.SpaceCategoryAnalyzeRequest;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.dto.SpaceRankAnalyzeRequest;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.dto.SpaceSizeAnalyzeRequest;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.dto.SpaceTagAnalyzeRequest;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.dto.SpaceUsageAnalyzeRequest;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.dto.SpaceUserAnalyzeRequest;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.vo.SpaceCategoryAnalyzeResponse;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.vo.SpaceSizeAnalyzeResponse;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.vo.SpaceTagAnalyzeResponse;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.vo.SpaceUsageAnalyzeResponse;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.vo.SpaceUserAnalyzeResponse;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.picture.domain.Picture;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureRepository;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureTableRouter;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.space.application.SpaceAppService;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceQuota;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 空间分析应用服务（只读上下文）。
 *
 * <p>它是"读模型"：不装载聚合行为，只从图片仓储 / 空间仓储取数后在内存里聚合。</p>
 *
 * <p>与重构前的差别：不再拼 {@code QueryWrapper + group by} 直接打 mapper，
 * 好处是跨空间、跨物理表的统计逻辑集中在一处，且不依赖 MyBatis-Plus；
 * 代价是统计会把命中的行读进内存——当前数据量（百级）完全可接受，
 * 数据量上来后应换成数据库侧聚合的查询 DAO。</p>
 */
@Service
public class SpaceAnalyzeAppServiceImpl implements SpaceAnalyzeAppService {

    private static final long KB = 1024L;

    private static final long MB = 1024L * 1024L;

    @Resource
    private UserAppService userAppService;

    @Resource
    private SpaceAppService spaceAppService;

    @Resource
    private PictureRepository pictureRepository;

    @Resource
    private PictureTableRouter pictureTableRouter;

    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        if (request.isQueryAll() || request.isQueryPublic()) {
            // 全部 / 公共图库：仅管理员
            ThrowUtils.throwIf(!userAppService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权访问公共图库");
            List<Picture> pictures = collect(request);
            long usedSize = pictures.stream().mapToLong(this::sizeOf).sum();
            SpaceUsageAnalyzeResponse response = new SpaceUsageAnalyzeResponse();
            response.setUsedSize(usedSize);
            response.setUsedCount((long) pictures.size());
            // 公共图库 / 全量没有上限，比例留空
            return response;
        }
        Long spaceId = request.getSpaceId();
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceAppService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        spaceAppService.checkSpaceAuth(loginUser, space);
        SpaceQuota quota = space.quota();
        SpaceUsageAnalyzeResponse response = new SpaceUsageAnalyzeResponse();
        response.setUsedSize(quota.getUsedSize());
        response.setMaxSize(quota.getMaxSize());
        response.setSizeUsageRatio(quota.sizeUsageRatio());
        response.setUsedCount(quota.getUsedCount());
        response.setMaxCount(quota.getMaxCount());
        response.setCountUsageRatio(quota.countUsageRatio());
        return response;
    }

    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(request, loginUser);
        Map<String, long[]> merged = new LinkedHashMap<>();
        for (Picture picture : collect(request)) {
            String category = picture.getCategory() == null ? "未分类" : picture.getCategory();
            long[] cell = merged.computeIfAbsent(category, key -> new long[2]);
            cell[0] += 1;
            cell[1] += sizeOf(picture);
        }
        return merged.entrySet().stream().map(entry -> {
            SpaceCategoryAnalyzeResponse response = new SpaceCategoryAnalyzeResponse();
            response.setCategory(entry.getKey());
            response.setCount(entry.getValue()[0]);
            response.setTotalSize(entry.getValue()[1]);
            return response;
        }).collect(Collectors.toList());
    }

    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(request, loginUser);
        Map<String, Long> tagCount = new LinkedHashMap<>();
        for (Picture picture : collect(request)) {
            String tags = picture.getTags();
            if (tags == null || !tags.startsWith("[")) {
                continue;
            }
            for (String tag : JSONUtil.toList(tags, String.class)) {
                tagCount.merge(tag, 1L, Long::sum);
            }
        }
        return tagCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(entry -> {
                    SpaceTagAnalyzeResponse response = new SpaceTagAnalyzeResponse();
                    response.setTag(entry.getKey());
                    response.setCount(entry.getValue());
                    return response;
                }).collect(Collectors.toList());
    }

    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(request, loginUser);
        List<Long> sizes = collect(request).stream().map(this::sizeOf).collect(Collectors.toList());
        Map<String, Long> ranges = new LinkedHashMap<>();
        ranges.put("<100KB", sizes.stream().filter(size -> size < 100 * KB).count());
        ranges.put("100KB-500KB", sizes.stream().filter(size -> size >= 100 * KB && size < 500 * KB).count());
        ranges.put("500KB-1MB", sizes.stream().filter(size -> size >= 500 * KB && size < MB).count());
        ranges.put(">1MB", sizes.stream().filter(size -> size >= MB).count());
        return ranges.entrySet().stream().map(entry -> {
            SpaceSizeAnalyzeResponse response = new SpaceSizeAnalyzeResponse();
            response.setSizeRange(entry.getKey());
            response.setCount(entry.getValue());
            return response;
        }).collect(Collectors.toList());
    }

    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(request, loginUser);
        Long userId = request.getUserId();
        String dimension = request.getTimeDimension();
        if (dimension == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }
        Map<String, Long> periodCount = new TreeMap<>();
        for (Picture picture : collect(request)) {
            if (userId != null && !userId.equals(picture.getUserId())) {
                continue;
            }
            if (picture.getCreateTime() == null) {
                continue;
            }
            periodCount.merge(periodOf(picture.getCreateTime(), dimension), 1L, Long::sum);
        }
        return periodCount.entrySet().stream().map(entry -> {
            SpaceUserAnalyzeResponse response = new SpaceUserAnalyzeResponse();
            response.setPeriod(entry.getKey());
            response.setCount(entry.getValue());
            return response;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!userAppService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看空间排行");
        return spaceAppService.listTopByUsedSize(request.getTopN());
    }

    // ==================== 内部方法 ====================

    /**
     * 权限校验：全量 / 公共图库仅管理员；私有空间要求归属或平台管理员
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest request, User loginUser) {
        if (request.isQueryAll() || request.isQueryPublic()) {
            ThrowUtils.throwIf(!userAppService.isAdmin(loginUser), ErrorCode.PARAMS_ERROR, "无权访问公共图库");
            return;
        }
        Long spaceId = request.getSpaceId();
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceAppService.getById(spaceId);
        ThrowUtils.throwIf(space == null || space.getId() <= 0, ErrorCode.PARAMS_ERROR);
        spaceAppService.checkSpaceAuth(loginUser, space);
    }

    /**
     * 按分析范围取图片：全量 -> 所有物理表；公共 -> picture_public；否则单空间表
     */
    private List<Picture> collect(SpaceAnalyzeRequest request) {
        if (request.isQueryAll()) {
            List<Picture> all = new ArrayList<>();
            for (Long scope : pictureTableRouter.allScopes()) {
                all.addAll(pictureRepository.listBySpace(scope));
            }
            return all;
        }
        if (request.isQueryPublic()) {
            return pictureRepository.listBySpace(null);
        }
        Long spaceId = request.getSpaceId();
        return spaceId == null ? Collections.emptyList() : pictureRepository.listBySpace(spaceId);
    }

    private long sizeOf(Picture picture) {
        return picture.getPicSize() == null ? 0L : picture.getPicSize();
    }

    /**
     * 时间维度分桶（对齐原 SQL 的 DATE_FORMAT / YEARWEEK 语义）
     */
    private String periodOf(java.util.Date date, String dimension) {
        LocalDate localDate = Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
        switch (dimension) {
            case "day":
                return localDate.toString();
            case "week":
                WeekFields weekFields = WeekFields.of(Locale.getDefault());
                return String.format("%d%02d", localDate.get(weekFields.weekBasedYear()),
                        localDate.get(weekFields.weekOfWeekBasedYear()));
            case "month":
                return String.format("%d-%02d", localDate.getYear(), localDate.getMonthValue());
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }
    }
}
