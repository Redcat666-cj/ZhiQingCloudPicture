package com.zhiqin.zhiqinpicturebackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.zhiqin.zhiqinpicturebackend.annotation.AuthCheck;
import com.zhiqin.zhiqinpicturebackend.api.aliyunai.AliYunAiApi;
import com.zhiqin.zhiqinpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.zhiqin.zhiqinpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.zhiqin.zhiqinpicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.zhiqin.zhiqinpicturebackend.api.imagesearch.ImageSearchApiFacede;
import com.zhiqin.zhiqinpicturebackend.api.imagesearch.model.ImageSearchResult;
import com.zhiqin.zhiqinpicturebackend.common.BaseResponse;
import com.zhiqin.zhiqinpicturebackend.common.DeleteRequest;
import com.zhiqin.zhiqinpicturebackend.common.ResultUtils;
import com.zhiqin.zhiqinpicturebackend.constant.UserConstant;
import com.zhiqin.zhiqinpicturebackend.domain.dto.*;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Picture;
import com.zhiqin.zhiqinpicturebackend.domain.entity.PictureTagCategory;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Space;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.domain.enums.PictureReviewStatusEnum;
import com.zhiqin.zhiqinpicturebackend.domain.enums.SpaceLevelEnum;
import com.zhiqin.zhiqinpicturebackend.domain.space.SpaceLevel;
import com.zhiqin.zhiqinpicturebackend.domain.vo.PictureVO;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.exception.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.manager.auth.SpaceUserAuthManager;
import com.zhiqin.zhiqinpicturebackend.manager.auth.StpKit;
import com.zhiqin.zhiqinpicturebackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.zhiqin.zhiqinpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.zhiqin.zhiqinpicturebackend.mapper.PictureMapper;
import com.zhiqin.zhiqinpicturebackend.mapper.UserMapper;
import com.zhiqin.zhiqinpicturebackend.service.PictureService;
import com.zhiqin.zhiqinpicturebackend.service.SpaceService;
import com.zhiqin.zhiqinpicturebackend.service.UserService;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tomcat.util.http.ResponseUtil;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/picture")
public class PictureController {
    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SpaceService spaceService;
    @Resource
    private AliYunAiApi aliYunAiApi;
    @Resource
    private SpaceUserAuthManager  spaceUserAuthManager;

    private final Cache<String, String> LOCAL_CHACE = Caffeine.newBuilder()
            .maximumSize(10000L)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    /**
     * 图片列表缓存的 Redis key 前缀（/list/page/vo/cache 写入与清除共用，避免不一致）
     */
    private static final String PICTURE_LIST_CACHE_KEY_PREFIX = "zhiqin_picture:listPictureVOByPage:";

    /**
     * 清除图片列表的两级缓存（本地 Caffeine + Redis）。
     * 图片的上传、编辑、删除、审核都会改变列表结果，相关写操作成功后必须调用，
     * 否则 /list/page/vo/cache 会命中旧缓存，出现“已删除的图片仍然显示”的问题。
     */
    private void clearPictureListCache() {
        LOCAL_CHACE.invalidateAll();
        Set<String> cacheKeys = stringRedisTemplate.keys(PICTURE_LIST_CACHE_KEY_PREFIX + "*");
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            stringRedisTemplate.delete(cacheKeys);
        }
    }


    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPicture(MultipartFile file, PictureUploadRequest pictureUploadRequest,HttpServletRequest request ) throws Exception {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(file, pictureUploadRequest, loginUser);
        // 图片列表已变化，清除两级缓存
        clearPictureListCache();
        return ResultUtils.success(pictureVO);


    }
    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) throws Exception {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        // 图片列表已变化，清除两级缓存
        clearPictureListCache();
        return ResultUtils.success(pictureVO);
    }

    /**
     * 批量抓取图片（仅管理员可用）
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Integer count = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        // 图片列表已变化，清除两级缓存
        clearPictureListCache();
        return ResultUtils.success(count);
    }

    /**
     * 删除图片
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) throws Exception {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        pictureService.deletePicture(loginUser,oldPicture);
        // 图片列表已变化，清除两级缓存
        clearPictureListCache();
        return ResultUtils.success(true);
    }

    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        //审核
        pictureService.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 图片列表已变化，清除两级缓存
        clearPictureListCache();
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) throws Exception {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        Long spaceId = picture.getSpaceId();
        Space space = null;
        if (spaceId != null) {
           // User loginUser = userService.getLoginUser(request);
           // pictureService.checkPictureAuth(loginUser,picture);
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);

        }
        User LoginUser = userService.getLoginUser(request);
        space = spaceService.getById(spaceId);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, LoginUser);
        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);
        // 获取封装类
        return ResultUtils.success(pictureVO);
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        // 动态分表后每张表只装一个空间的图片，管理员全局列表需要跨表合并
        Page<Picture> picturePage = pictureService.pageAcrossSpaces(pictureQueryRequest);
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        //权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            //公开图库
            //普通用户只能看到审核通过的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        }else{
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            //私有空间
//            User loginUser = userService.getLoginUser(request);
//            Space space = spaceService.getById(spaceId);
//            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
//            if(!loginUser.getId().equals(space.getUserId())){
//               throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
        }
        // 查询数据库（动态分表：按 spaceId 路由到 picture_public / picture_{spaceId}）
        Page<Picture> picturePage = pictureService.pageByRequest(pictureQueryRequest, new Page<>(current, size));

        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

        // 获取封装类
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        //权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            //公开图库
            //普通用户只能看到审核通过的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        }else{
            // 空间图库：有「查看图片」权限即可（私有空间=本人/管理员，团队空间=团队成员）
            // 不能只判断空间创建者，否则团队空间的协作者、浏览者都看不到图片列表
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
        }
        //构建缓存key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5Hex(queryCondition.getBytes());
        String redisKey = PICTURE_LIST_CACHE_KEY_PREFIX + hashKey;
        String cacheValue = LOCAL_CHACE.getIfPresent(redisKey);
        if (cacheValue != null) {
            Page<PictureVO> cachePicture = JSONUtil.toBean(cacheValue, Page.class);
            return ResultUtils.success(cachePicture);
        }

        //caffeine挂了查询redis
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        cacheValue = valueOps.get(redisKey);
        if (cacheValue != null) {
          Page<PictureVO> cachePicture = JSONUtil.toBean(cacheValue, Page.class);
          return ResultUtils.success(cachePicture);
        }
        // 查询数据库并转成 VO 再缓存。实体里的 tags 是 JSON 字符串，直接缓存会让前端把每个字符渲染成标签
        // 动态分表：按 spaceId 路由到 picture_public / picture_{spaceId}
        Page<Picture> picturePage = pictureService.pageByRequest(pictureQueryRequest, new Page<>(current, size));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        String voCacheValue = JSONUtil.toJsonStr(pictureVOPage);
        LOCAL_CHACE.put(redisKey, voCacheValue);
        //防止雪崩机制
        int cacheExpireTime = 300 +  RandomUtil.randomInt(0, 300);
        valueOps.set(redisKey, voCacheValue, cacheExpireTime, TimeUnit.SECONDS);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) throws Exception {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        pictureService.editPicture(pictureEditRequest,request);
        // 图片列表已变化，清除两级缓存
        clearPictureListCache();
        return ResultUtils.success(true);
    }


    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }


    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doReviewPicture(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        // 审核状态会影响公开图库的可见性，清除两级缓存
        clearPictureListCache();
        return ResultUtils.success(true);


    }

    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }


    /**
     * 以图搜图
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        List<ImageSearchResult> resultList = ImageSearchApiFacede.searchImage(oldPicture.getUrl());
        return ResultUtils.success(resultList);
    }

    @PostMapping("/search/color")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest,HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> result = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureBatch(pictureEditByBatchRequest, loginUser);
        clearPictureListCache();
        return ResultUtils.success(true);
    }

    /**
     * 创建AI扩图任务
     * @param createPictureOutPaintingTaskRequest
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, HttpServletRequest request
    ) throws Exception {
        if(createPictureOutPaintingTaskRequest == null|| createPictureOutPaintingTaskRequest.getPictureId() <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询AI扩图任务
     * @param outPaintingTaskId
     * @return
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getOutPaintingTask(String outPaintingTaskId) {
        ThrowUtils.throwIf(outPaintingTaskId == null, ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse outPaintingTask = aliYunAiApi.getOutPaintingTask(outPaintingTaskId);
        return ResultUtils.success(outPaintingTask);
    }

}
