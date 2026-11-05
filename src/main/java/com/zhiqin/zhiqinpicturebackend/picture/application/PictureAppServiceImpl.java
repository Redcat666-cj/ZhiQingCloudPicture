package com.zhiqin.zhiqinpicturebackend.picture.application;

import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;

import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureQuery;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureRepository;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureTableRouter;

import com.zhiqin.zhiqinpicturebackend.storage.domain.UploadPictureResult;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhiqin.zhiqinpicturebackend.ai.domain.OutPaintingPort;
import com.zhiqin.zhiqinpicturebackend.ai.domain.dto.CreateOutPaintingTaskResponse;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserConstant;
import com.zhiqin.zhiqinpicturebackend.picture.interfaces.dto.*;
import com.zhiqin.zhiqinpicturebackend.picture.domain.Picture;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureReviewStatusEnum;
import com.zhiqin.zhiqinpicturebackend.picture.application.dto.PictureVO;
import com.zhiqin.zhiqinpicturebackend.identity.application.dto.UserVO;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.ai.infrastructure.BingImageFetcher;
import com.zhiqin.zhiqinpicturebackend.space.infrastructure.StpKit;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUserPermissionConstant;
import com.zhiqin.zhiqinpicturebackend.storage.domain.StorageService;
import com.zhiqin.zhiqinpicturebackend.picture.infrastructure.sharding.PictureTableLocator;
import com.zhiqin.zhiqinpicturebackend.picture.infrastructure.sharding.SpaceTableContext;
import com.zhiqin.zhiqinpicturebackend.picture.infrastructure.sharding.SpaceTableManager;
import com.zhiqin.zhiqinpicturebackend.picture.application.PictureAppService;
import com.zhiqin.zhiqinpicturebackend.space.application.SpaceAppService;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import com.zhiqin.zhiqinpicturebackend.picture.domain.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.jsoup.internal.StringUtil;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author 崔健
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2026-09-10 19:39:21
*/
@Slf4j
@Service
public class PictureAppServiceImpl implements PictureAppService {

    @Resource
    private BingImageFetcher bingImageFetcher;


    @Resource
    private StorageService storageService;

    @Resource
    private UserAppService UserAppService;
    @Resource
    private SpaceAppService SpaceAppService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private OutPaintingPort outPaintingPort;
    @Resource
    private PictureRepository pictureRepository;

    @Resource
    private PictureTableRouter pictureTableRouter;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 批量抓取图片去重：
     * - URL 索引（抓取地址的 MD5）：拦住"同地址再次抓取"（跨批次、同词重抓）
     * - 内容指纹索引（图片文件的 MD5）：拦住"不同地址、同一张图"（murl 失败回退 turl、
     *   不同搜索词命中同一图、Bing 换 CDN 域名等）
     * Redis Set 是全局的（picture 表按空间分表，没有全局唯一索引可用），带过期防膨胀。
     */
    private static final String BATCH_URL_DEDUP_KEY = "picture:batch:url:";
    private static final String BATCH_MD5_DEDUP_KEY = "picture:batch:md5:";
    private static final long DEDUP_KEY_TTL_DAYS = 30;


    @Override
    public PictureVO uploadPicture(Object file, PictureUploadRequest pictureUploadRequest, User loginUser) throws Exception {
        if(file == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片为空");
        }
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR);
        //判断空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if(spaceId != null){
            Space space = SpaceAppService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR,"空间不存在");
//            if(!loginUser.getId().equals(space.getUserId())){
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无权限");
//            }
            //检查额度
            if(space.getTotalCount()>=space.getMaxCount()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间条数不足");
            }
            if(space.getTotalSize()>=space.getMaxSize()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间不足");
            }
        }
        //判断是新增还是删除
        Long pictureId = null;
        String picName = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
            picName = pictureUploadRequest.getPicName();
        }
        //如果是更新判断图片是否存在
        Picture oldPicture = null;
        if (!(pictureId == null)) {
            oldPicture = pictureRepository.findById(pictureId);
            ThrowUtils.throwIf(oldPicture==null, ErrorCode.PARAMS_ERROR, "图片不存在");
            if(!loginUser.getId().equals(oldPicture.getUserId())){
                // 团队空间的协作编辑：有「编辑图片」权限的成员也可以覆盖别人上传的图片，
                // 否则协同编辑里除上传者之外的人都保存不了
                boolean canOverwriteInSpace = oldPicture.getSpaceId() != null
                        && StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_EDIT);
                ThrowUtils.throwIf(!canOverwriteInSpace, ErrorCode.NO_AUTH_ERROR);
            }
            //校验空间是否一致
            //没有spaceId复用原有的spaceid
            if(spaceId == null){
                if(oldPicture.getSpaceId() != null){
                    spaceId = oldPicture.getSpaceId();
                }
            }else {
                //传了spaceId必须和原来图片的spaceID一样
                if(!spaceId.equals(oldPicture.getSpaceId())){
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"空间id不一致");
                }

            }
        }
        //上传图片，得到图片信息
        // 按照用户 id 划分目录 =>按照空间划分目录
        String uploadPathPrefix;
        if(spaceId == null){
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        }else{
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        // 根据 file 类型区分上传方式
        UploadPictureResult uploadPictureResult = storageService.uploadPicture(file, uploadPathPrefix);

        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setSpaceId(spaceId); //指定空间
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 名称优先使用传入的自定义名称，否则使用原始文件名
        picture.setName(StrUtil.isNotBlank(picName) ? picName : uploadPictureResult.getPicName());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setPicColor(uploadPictureResult.getPicColor());
        picture.setUserId(loginUser.getId());
        //审核
        this.fillReviewParams(picture, loginUser);
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        //更新额度
        // 覆盖图片只补「大小差值」，张数不变；否则每保存一次都会虚增容量和张数
        // （lambda 里只能引用 effectively final 变量，所以先在这里算好）
        long deltaSize = picture.getPicSize() == null ? 0L : picture.getPicSize();
        int deltaCount = 1;
        if (pictureId != null) {
            if (oldPicture != null && oldPicture.getPicSize() != null) {
                deltaSize -= oldPicture.getPicSize();
            }
            deltaCount = 0;
        }
        final long finalDeltaSize = deltaSize;
        final int finalDeltaCount = deltaCount;
        //动态分表：确保该空间的物理表已存在（spaceId 为 null 就是公共图库 picture_public）
        pictureTableRouter.ensureSpaceTable(spaceId);
        //开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status ->
        {
            //插入数据
            boolean flag = pictureRepository.save(picture) != null;
            ThrowUtils.throwIf(!flag, ErrorCode.OPERATION_ERROR, "上传失败");
            if(finalSpaceId != null){
                boolean update = SpaceAppService.changeUsage(finalSpaceId, finalDeltaSize, finalDeltaCount);
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR,"更新失败");
            }

            return picture;
        });
        //todo 更新可以清理旧图片

        return PictureVO.objToVo(picture);


    }




    /**
     * 请求对象 -> 领域查询对象
     */
    private PictureQuery toQuery(PictureQueryRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        PictureQuery query = new PictureQuery();
        query.setId(request.getId());
        query.setName(request.getName());
        query.setIntroduction(request.getIntroduction());
        query.setCategory(request.getCategory());
        query.setTags(request.getTags());
        query.setPicSize(request.getPicSize());
        query.setPicWidth(request.getPicWidth());
        query.setPicHeight(request.getPicHeight());
        query.setPicScale(request.getPicScale());
        query.setPicFormat(request.getPicFormat());
        query.setSearchText(request.getSearchText());
        query.setUserId(request.getUserId());
        query.setSpaceId(request.getSpaceId());
        query.setNullSpaceId(request.isNullSpaceId());
        query.setReviewStatus(request.getReviewStatus());
        query.setReviewMessage(request.getReviewMessage());
        query.setReviewerId(request.getReviewerId());
        query.setStartEditTime(request.getStartEditTime());
        query.setEndEditTime(request.getEndEditTime());
        query.setSortField(request.getSortField());
        if (request.getSortOrder() != null) {
            query.setSortOrder(request.getSortOrder());
        }
        query.setCurrent(request.getCurrent());
        query.setSize(request.getPageSize());
        return query;
    }

    @Override
    public Picture getById(Long id) {
        return pictureRepository.findById(id);
    }

    @Override
    public boolean updateById(Picture picture) {
        return pictureRepository.update(picture);
    }

    /**
     * 单空间分页查询：request.spaceId 为 null 时查公共图库
     */
    @Override
    public Page<Picture> pageByRequest(PictureQueryRequest request, Page<Picture> page) {
        return toPage(pictureRepository.page(toQuery(request)));
    }

    /**
     * 跨空间分页查询（管理员全局列表）：仓储内部逐表合并后排序截页
     */
    @Override
    public Page<Picture> pageAcrossSpaces(PictureQueryRequest request) {
        PictureQuery query = toQuery(request);
        query.setCrossSpaces(true);
        return toPage(pictureRepository.page(query));
    }

    /**
     * 领域分页结果 -> MyBatis-Plus Page（对外契约保持不变）
     */
    private Page<Picture> toPage(PageResult<Picture> result) {
        Page<Picture> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(result.getRecords());
        return page;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = UserAppService.getById(userId);
            UserVO userVO = UserAppService.getUserVo(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }
    /**
     * 颜色查询服务
     *
     *
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser)
    {
        //1.校验参数
        ThrowUtils.throwIf(spaceId == null|| StringUtil.isBlank(picColor),ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null,ErrorCode.NO_AUTH_ERROR);
        //2.检验空间权限
        Space space = SpaceAppService.getById(spaceId);
        ThrowUtils.throwIf(space == null ,ErrorCode.PARAMS_ERROR);
        if(!space.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //3.查询该空间下所有图片(必须有主色调)
        //动态分表：本条查询路由到 picture_{spaceId}
        List<Picture> pictureList = pictureRepository.listBySpace(spaceId).stream()
                .filter(picture -> StrUtil.isNotBlank(picture.getPicColor()))
                .collect(Collectors.toList());
        //如果没有图片返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }
        //将目标颜色转为Color对象
        Color targetColor = Color.decode(picColor);
        //4.计算相似值并排序
       List<Picture> sortedPictures =  pictureList.stream().sorted(Comparator.comparingDouble(picture ->{
            //获取主色调
            String hexColor = picture.getPicColor();
            //没有主色调的图片放最后
            if(StringUtil.isBlank(hexColor)){
                return Double.MAX_VALUE;
            }
            Color pictureColor = Color.decode(hexColor);
            //越大越相似
            return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);


        }))
        //取前12个
                .limit(12)
                .collect(Collectors.toList());

        //转换为PictureVo
      return sortedPictures.stream().map(PictureVO::objToVo)
               .collect(Collectors.toList());


    }





    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = UserAppService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(UserAppService.getUserVo(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User user) {
        //1.校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum byValue = PictureReviewStatusEnum.findByValue(reviewStatus);
        //2.判断图片是否存在
        Picture picture = pictureRepository.findById(id);
        if (picture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"图片不存在");
        }
        //3.校验图片审核状态是否重复
        if (picture.getReviewStatus().equals(byValue)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请勿重复审核");
        }
        //4.数据库操作
        //为了避免所有字段更新new一个picture
        Picture updatePicture = BeanUtil.copyProperties(pictureReviewRequest, Picture.class);
        updatePicture.setReviewerId(user.getId());
        updatePicture.setReviewTime(new Date());
        boolean flag = pictureRepository.update(updatePicture);
        ThrowUtils.throwIf(!flag,ErrorCode.OPERATION_ERROR);
    }

    /**
     * 过审
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser){

            // 管理员自动过审
            if(loginUser.getUserRole().equals(UserConstant.ADMIN_ROLE)){
                picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
                picture.setReviewerId(loginUser.getId());
                picture.setReviewMessage("管理员自动过审");
                picture.setReviewTime(new Date());
            }else{
                picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
            }
    }

    @Override
    public void deletePicture(User loginUser, Picture picture) throws Exception {
       // checkPictureAuth(loginUser,picture);
        //操作数据库
        Long finalSpaceId = picture.getSpaceId();
        transactionTemplate.execute(status ->
        {
            //删除数据
            boolean flag = pictureRepository.removeById(picture.getId());
            ThrowUtils.throwIf(!flag, ErrorCode.OPERATION_ERROR, "上传失败");
            if(finalSpaceId != null){
                long deltaSize = picture.getPicSize() == null ? 0L : picture.getPicSize();
                boolean update = SpaceAppService.changeUsage(finalSpaceId, -deltaSize, -1L);
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR,"更新失败");
            }

            return picture;
        });
        try {
            ((PictureAppService) AopContext.currentProxy()).delteleObject(picture);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) throws Exception {
        Long spaceId = picture.getSpaceId();
        Long id = loginUser.getId();
        if(spaceId == null){
            //公共图库仅本人和管理者可操作
            if(!picture.getUserId().equals(id)&& !UserAppService.isAdmin(loginUser)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }else {
            //私有空间 只用私有者可操作
            if(!picture.getUserId().equals(id)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    @Async //记得在启动类打上enable 一般不建议使用默认线程池
    @Override
    public void delteleObject(Picture oldPicture) throws Exception {
        // @Async 会切到别的线程，ThreadLocal 不会自动传过去，必须在这里重新把上下文设上
        Long previous = SpaceTableContext.get();
        SpaceTableContext.set(oldPicture.getSpaceId());
        try {
            // 判断该图片是否被多条记录使用
            String url = oldPicture.getUrl();
            long count = pictureRepository.listBySpace(oldPicture.getSpaceId()).stream()
                    .filter(item -> url.equals(item.getUrl())).count();
            if (count > 1) {
                return;
            }
            // FIXME 注意，这里的 url 包含了域名，实际上只要传 key 值（存储路径）就够了
            storageService.deleteObject(url);
            //清理缩略图
            String thumbnailUrl = oldPicture.getThumbnailUrl();
            if (StrUtil.isNotBlank(thumbnailUrl)) {
                storageService.deleteObject(thumbnailUrl);
            }
        } finally {
            SpaceTableContext.set(previous);
        }

    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request) throws Exception {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = pictureRepository.findById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = UserAppService.getLoginUser(request);
        //审核
        fillReviewParams(picture, loginUser);
        // 仅本人或管理员可编辑
        //checkPictureAuth(loginUser,oldPicture);
        // 操作数据库
        boolean result = pictureRepository.update(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }



    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(StrUtil.isBlank(searchText), ErrorCode.PARAMS_ERROR, "搜索词不能为空");
        ThrowUtils.throwIf(count == null || count <= 0, ErrorCode.PARAMS_ERROR, "抓取数量不合法");

        // 统一命名前缀：优先使用自定义前缀，为空则回退到搜索词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }

        // 抓取 Bing 图片地址（fetcher 内部已做单页 URL 去重）
        List<BingImageFetcher.BingImage> images = bingImageFetcher.fetchImages(searchText, count);

        // 逐条上传入库，单张失败不影响整体；原图失败回退到 Bing 缩略图。
        // 去重三层：地址已抓过（Redis URL 索引）→ 跳过；
        //          内容已存在（下载后 MD5 查 Redis 指纹索引）→ 跳过并不再回退 turl；
        //          上传成功 → 登记地址与内容指纹，供后续批次判重。
        int successCount = 0;
        for (BingImageFetcher.BingImage image : images) {
            if (isDuplicateUrl(image.getMurl())) {
                continue;
            }
            PictureUploadRequest uploadRequest = new PictureUploadRequest();
            uploadRequest.setPicName(String.format("%s_%d", namePrefix, successCount + 1));
            UploadOutcome outcome = tryUploadWithDedup(image.getMurl(), uploadRequest, loginUser);
            if (outcome == UploadOutcome.DUPLICATE) {
                // 内容已存在：同一张图的缩略图也是重复内容，没必要再试 turl
                continue;
            }
            if (outcome == UploadOutcome.FAILED && StrUtil.isNotBlank(image.getTurl())
                    && !isDuplicateUrl(image.getTurl())) {
                outcome = tryUploadWithDedup(image.getTurl(), uploadRequest, loginUser);
            }
            if (outcome == UploadOutcome.SUCCESS) {
                successCount++;
            }
        }
        return successCount;
    }

    /** 单张抓取上传的三种结局 */
    private enum UploadOutcome { SUCCESS, FAILED, DUPLICATE }

    /**
     * 该抓取地址是否已成功入库过（跨批次判重）。
     */
    private boolean isDuplicateUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return true;
        }
        String urlHash = DigestUtils.md5Hex(url.trim());
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(BATCH_URL_DEDUP_KEY, urlHash));
    }

    /**
     * 上传并做内容级判重：下载图片算 MD5，命中指纹索引直接判定重复（文件不落 COS）。
     * 上传成功后登记地址与内容指纹；上传失败则回滚指纹，避免下次同图被误判为重复。
     */
    private UploadOutcome tryUploadWithDedup(String url, PictureUploadRequest uploadRequest, User loginUser) {
        if (StrUtil.isBlank(url)) {
            return UploadOutcome.FAILED;
        }
        String urlHash = DigestUtils.md5Hex(url.trim());
        String md5 = null;
        try {
            byte[] bytes = downloadImage(url);
            if (bytes == null || bytes.length == 0) {
                return UploadOutcome.FAILED;
            }
            md5 = DigestUtils.md5Hex(bytes);
            Boolean existed = stringRedisTemplate.opsForSet().isMember(BATCH_MD5_DEDUP_KEY, md5);
            if (Boolean.TRUE.equals(existed)) {
                log.info("批量抓取命中内容指纹，跳过：url={}", url);
                return UploadOutcome.DUPLICATE;
            }
            // 竞态说明：两个批次并发抓到同一张图可能同时走到这里（都查不到指纹、都上传）。
            // SADD 是原子的：后写者返回 0，以此做“先到先得”，后到的一张按重复处理。
            Long first = stringRedisTemplate.opsForSet().add(BATCH_MD5_DEDUP_KEY, md5);
            if (first != null && first == 0) {
                return UploadOutcome.DUPLICATE;
            }
            // 内容已在内存，直接走字节上传模板（避免 URL 模板再下载一次）
            PictureVO uploaded = uploadPicture(bytes, uploadRequest, loginUser);
            if (uploaded == null) {
                return UploadOutcome.FAILED;
            }
            stringRedisTemplate.opsForSet().add(BATCH_URL_DEDUP_KEY, urlHash);
            expireDedupKeysIfNeeded();
            return UploadOutcome.SUCCESS;
        } catch (Exception e) {
            log.warn("图片抓取上传失败，url = {}", url, e);
            // 上传失败要撤销指纹登记，否则这张图永远进不来
            if (md5 != null) {
                stringRedisTemplate.opsForSet().remove(BATCH_MD5_DEDUP_KEY, md5);
            }
            return UploadOutcome.FAILED;
        }
    }

    /** 下载图片的浏览器伪装头：多数图源有防盗链，裸请求会 403 */
    private static final String CRAWL_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 下载图片到内存（批量抓取场景图片不大；超大图会走异常路径按失败处理）。
     * 带 UA 与 Referer 规避防盗链，与 UrlPictureUpload 的下载行为保持一致。
     */
    private byte[] downloadImage(String url) {
        cn.hutool.http.HttpResponse response = null;
        try {
            response = cn.hutool.http.HttpRequest.get(url)
                    .header("User-Agent", CRAWL_USER_AGENT)
                    .header("Referer", url)
                    .timeout(15000)
                    .execute();
            if (response.getStatus() != cn.hutool.http.HttpStatus.HTTP_OK) {
                log.warn("下载图片失败 HTTP {}：url={}", response.getStatus(), url);
                return null;
            }
            return response.bodyBytes();
        } catch (Exception e) {
            log.warn("下载图片失败：url={}", url);
            return null;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * 去重索引设置过期，避免无限膨胀（每次登记后顺手刷新，整 key 30 天）。
     */
    private void expireDedupKeysIfNeeded() {
        stringRedisTemplate.expire(BATCH_URL_DEDUP_KEY, java.time.Duration.ofDays(DEDUP_KEY_TTL_DAYS));
        stringRedisTemplate.expire(BATCH_MD5_DEDUP_KEY, java.time.Duration.ofDays(DEDUP_KEY_TTL_DAYS));
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void editPictureBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser){
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        // 动态分表：一批图片都在同一个空间里，设一次上下文即可
        SpaceTableContext.run(pictureEditByBatchRequest.getSpaceId(),
                () -> doEditPictureBatch(pictureEditByBatchRequest, loginUser));
    }

    private void doEditPictureBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser){
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String nameRule = pictureEditByBatchRequest.getNameRule();
        //1.参数校验。不传 spaceId 表示编辑公共图库
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        //2.校验权限并查询图片。命名规则需要原名称和分类，所以一并查出
        List<Picture> pictureList;
        if (spaceId == null) {
//            if (!UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可以批量编辑公共图库");
//            }
            pictureList = pictureRepository.listBySpace(null).stream()
                    .filter(item -> pictureIdList.contains(item.getId()))
                    .collect(Collectors.toList());
        } else {
            Space space = SpaceAppService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NO_AUTH_ERROR);
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            pictureList = pictureRepository.listBySpace(spaceId).stream()
                    .filter(item -> pictureIdList.contains(item.getId()))
                    .collect(Collectors.toList());
        }
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureList), ErrorCode.PARAMS_ERROR, "没有可编辑的图片");
        //4.更新分类和标签及批量重命名
        pictureList.forEach(picture -> {
            if(StrUtil.isNotBlank(category)){
                picture.setCategory(category);
            }
            if(CollUtil.isNotEmpty(tags)){
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        fillPictureWithNameRule(pictureList, nameRule);
        //5.批量更新
        boolean result = pictureList.stream().allMatch(pictureRepository::update);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

    }

    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if(CollUtil.isEmpty(pictureList)||StrUtil.isBlank(nameRule)){
            return;
        }
        long count = 1;
        String dateText = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        try {
            for (Picture picture : pictureList) {
                String originName = StrUtil.nullToEmpty(picture.getName());
                String category = StrUtil.nullToEmpty(picture.getCategory());
                String pictureName = nameRule
                        .replace("{序号}", String.valueOf(count++))
                        .replace("{原名称}", originName)
                        .replace("{分类}", category)
                        .replace("{日期}", dateText);
                picture.setName(pictureName);
            }
        } catch (Exception e) {
           log.error("名称解析失败",e);
           throw new BusinessException(ErrorCode.OPERATION_ERROR,"名称解析失败");
        }


    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) throws Exception {
        //获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture =  Optional.ofNullable(pictureRepository.findById(pictureId)).orElseThrow(
                ()-> new BusinessException(ErrorCode.NOT_FOUND_ERROR)
        );
        //权限校验
        //checkPictureAuth(loginUser,picture);
        float xScale = 1.5f;
        float yScale = 1.5f;
        if (createPictureOutPaintingTaskRequest.getParameters() != null) {
            if (createPictureOutPaintingTaskRequest.getParameters().getXScale() != null) {
                xScale = createPictureOutPaintingTaskRequest.getParameters().getXScale();
            }
            if (createPictureOutPaintingTaskRequest.getParameters().getYScale() != null) {
                yScale = createPictureOutPaintingTaskRequest.getParameters().getYScale();
            }
        }
        return outPaintingPort.createOutPaintingTask(
                picture.getUrl(),
                xScale,
                yScale,
                picture.getPicWidth(),
                picture.getPicHeight(),
                createPictureOutPaintingTaskRequest.getUserPrompt());
    }


    /**
     * 图片被浏览：浏览数原子 +1 并刷新最近浏览时间；
     * 已降沉的图重新有人看时自动回温为标准存储（低频取回更贵更慢，热了就该回来）。
     * 任何失败只记日志，不影响浏览主流程。
     */
    @Override
    public void onPictureViewed(Picture picture) {
        if (picture == null || picture.getId() == null) {
            return;
        }
        try {
            pictureRepository.incrementReviews(picture.getId());
            if (picture.isDemoted()) {
                storageService.restoreToStandard(picture.getUrl());
                if (StrUtil.isNotBlank(picture.getThumbnailUrl())) {
                    storageService.restoreToStandard(picture.getThumbnailUrl());
                }
                pictureRepository.markDemoted(picture.getId(), false);
                log.info("降沉图片被浏览，已恢复标准存储：pictureId={} url={}", picture.getId(), picture.getUrl());
            }
        } catch (Exception e) {
            log.warn("浏览计数/回温失败，pictureId={}", picture.getId(), e);
        }
    }
}




