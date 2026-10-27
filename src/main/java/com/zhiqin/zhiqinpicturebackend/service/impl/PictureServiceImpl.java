package com.zhiqin.zhiqinpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhiqin.zhiqinpicturebackend.api.aliyunai.AliYunAiApi;
import com.zhiqin.zhiqinpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.zhiqin.zhiqinpicturebackend.constant.UserConstant;
import com.zhiqin.zhiqinpicturebackend.domain.dto.*;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Picture;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Space;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.domain.enums.PictureReviewStatusEnum;
import com.zhiqin.zhiqinpicturebackend.domain.vo.PictureVO;
import com.zhiqin.zhiqinpicturebackend.domain.vo.UserVO;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.exception.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.manager.BingImageFetcher;
import com.zhiqin.zhiqinpicturebackend.manager.CosManager;
import com.zhiqin.zhiqinpicturebackend.manager.table.PictureTableLocator;
import com.zhiqin.zhiqinpicturebackend.manager.table.SpaceTableContext;
import com.zhiqin.zhiqinpicturebackend.manager.table.SpaceTableManager;
import com.zhiqin.zhiqinpicturebackend.manager.upload.FilePictureUpload;
import com.zhiqin.zhiqinpicturebackend.manager.upload.PictureUploadTemplate;
import com.zhiqin.zhiqinpicturebackend.manager.upload.UrlPictureUpload;
import com.zhiqin.zhiqinpicturebackend.service.PictureService;
import com.zhiqin.zhiqinpicturebackend.mapper.PictureMapper;
import com.zhiqin.zhiqinpicturebackend.service.SpaceService;
import com.zhiqin.zhiqinpicturebackend.service.UserService;
import com.zhiqin.zhiqinpicturebackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.internal.StringUtil;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService {

    @Resource
    private BingImageFetcher bingImageFetcher;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UserService userService;
    @Resource
    private CosManager cosManager;
    @Resource
    private SpaceService spaceService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private AliYunAiApi aliYunAiApi;
    @Resource
    private SpaceTableManager spaceTableManager;
    @Resource
    private PictureTableLocator pictureTableLocator;

    // ==================== 动态分表：picture -> picture_public / picture_{spaceId} ====================
    // 下面几个重写方法专治「手里只有一个图片 id」的场景（get / update / delete / review / 外绘）
    // 旧代码只知道 id，不知道属于哪个空间；这里先反查出空间，把 SpaceTableContext 设好，
    // 再交给父类执行 SQL。所以 controller、mapper 一行都不用改。

    @Override
    public Picture getById(Serializable id) {
        Long previous = SpaceTableContext.get();
        SpaceTableContext.set(pictureTableLocator.locateSpaceId(toPictureId(id)));
        try {
            return super.getById(id);
        } finally {
            SpaceTableContext.set(previous);
        }
    }

    @Override
    public boolean updateById(Picture entity) {
        // entity 上带了 spaceId 就直接用，否则按 id 反查
        Long spaceId = entity.getSpaceId() != null
                ? entity.getSpaceId()
                : pictureTableLocator.locateSpaceId(entity.getId());
        Long previous = SpaceTableContext.get();
        SpaceTableContext.set(spaceId);
        try {
            return super.updateById(entity);
        } finally {
            SpaceTableContext.set(previous);
        }
    }

    @Override
    public boolean removeById(Serializable id) {
        Long pictureId = toPictureId(id);
        Long previous = SpaceTableContext.get();
        SpaceTableContext.set(pictureTableLocator.locateSpaceId(pictureId));
        try {
            boolean removed = super.removeById(id);
            // 图片没了，id -> 空间 的缓存也要清掉
            pictureTableLocator.evict(pictureId);
            return removed;
        } finally {
            SpaceTableContext.set(previous);
        }
    }

    @Override
    public boolean removeById(Picture entity) {
        return removeById((Serializable) entity.getId());
    }

    @Override
    public boolean saveOrUpdate(Picture entity) {
        Long previous = SpaceTableContext.get();
        SpaceTableContext.set(entity.getSpaceId());
        try {
            return super.saveOrUpdate(entity);
        } finally {
            SpaceTableContext.set(previous);
        }
    }

    @Override
    public boolean save(Picture entity) {
        Long previous = SpaceTableContext.get();
        SpaceTableContext.set(entity.getSpaceId());
        try {
            return super.save(entity);
        } finally {
            SpaceTableContext.set(previous);
        }
    }

    /**
     * 批量保存：一批里可能混着多个空间的图片，按 spaceId 分组，各写各的表
     */
    @Override
    public boolean saveBatch(Collection<Picture> entityList) {
        return saveBatch(entityList, DEFAULT_BATCH_SIZE);
    }

    @Override
    public boolean saveBatch(Collection<Picture> entityList, int batchSize) {
        // groupingBy 不允许 null key，用 0 暂代公共图库
        Map<Long, List<Picture>> grouped = entityList.stream()
                .collect(Collectors.groupingBy(picture -> picture.getSpaceId() == null ? 0L : picture.getSpaceId()));
        boolean result = true;
        for (Map.Entry<Long, List<Picture>> entry : grouped.entrySet()) {
            Long previous = SpaceTableContext.get();
            SpaceTableContext.set(entry.getKey() == 0L ? null : entry.getKey());
            try {
                result = super.saveBatch(entry.getValue(), batchSize) && result;
            } finally {
                SpaceTableContext.set(previous);
            }
        }
        return result;
    }

    /**
     * Serializable 形式的 id 统一转成 Long
     */
    private static Long toPictureId(Serializable id) {
        if (id instanceof Number) {
            return ((Number) id).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(id));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public PictureVO uploadPicture(Object file, PictureUploadRequest pictureUploadRequest, User loginUser) throws Exception {
        if(file == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片为空");
        }
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR);
        //判断空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if(spaceId != null){
            Space space = spaceService.getById(spaceId);
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
        if (!(pictureId == null)) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture==null, ErrorCode.PARAMS_ERROR, "图片不存在");
            if(!loginUser.getId().equals(oldPicture.getUserId())){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
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
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if(file instanceof String){
            pictureUploadTemplate = urlPictureUpload;

        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(file, uploadPathPrefix);

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
        //动态分表：确保该空间的物理表已存在（spaceId 为 null 就是公共图库 picture_public）
        spaceTableManager.ensureSpaceTable(spaceId);
        //开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status ->
        {
            //插入数据
            boolean flag = saveOrUpdate(picture);
            ThrowUtils.throwIf(!flag, ErrorCode.OPERATION_ERROR, "上传失败");
            if(finalSpaceId != null){
                boolean update = spaceService.lambdaUpdate().eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + " + 1)
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR,"更新失败");
            }

            return picture;
        });
        //todo 更新可以清理旧图片

        return PictureVO.objToVo(picture);


    }




    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        if (pictureQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 从多字段中搜索
        if (StrUtil.isNotEmpty(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText).or()
                    .like("introduction", searchText));
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);

        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        //排序
        queryWrapper.orderBy(ObjUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 单空间分页查询：request.spaceId 为 null 时查公共图库
     */
    @Override
    public Page<Picture> pageByRequest(PictureQueryRequest request, Page<Picture> page) {
        QueryWrapper<Picture> queryWrapper = getQueryWrapper(request);
        Long previous = SpaceTableContext.get();
        SpaceTableContext.set(request.getSpaceId());
        try {
            return super.page(page, queryWrapper);
        } finally {
            SpaceTableContext.set(previous);
        }
    }

    /**
     * 跨空间分页查询（管理员全局列表）。
     *
     * <p>表名是运行期才确定的，没有任何一张物理表能一次装下所有空间的图片，
     * 所以这里逐个表查出来、合并、按排序字段重排、再截取当页。
     * 空间数量多了会变慢，是「每个空间一张表」这种方案的固有代价。</p>
     */
    @Override
    public Page<Picture> pageAcrossSpaces(PictureQueryRequest request) {
        QueryWrapper<Picture> queryWrapper = getQueryWrapper(request);
        long current = request.getCurrent();
        long pageSize = request.getPageSize();
        List<Picture> all = new ArrayList<>();
        Long previous = SpaceTableContext.get();
        try {
            // 公共图库
            SpaceTableContext.set(null);
            all.addAll(super.list(queryWrapper));
            // 每个空间
            for (Long spaceId : spaceTableManager.listSpaceIds()) {
                SpaceTableContext.set(spaceId);
                all.addAll(super.list(queryWrapper));
            }
        } finally {
            SpaceTableContext.set(previous);
        }
        all.sort(buildComparator(request.getSortField(), request.getSortOrder()));
        Page<Picture> page = new Page<>(current, pageSize, all.size());
        long from = Math.max(0, (current - 1) * pageSize);
        int to = (int) Math.min(all.size(), from + pageSize);
        page.setRecords(from >= all.size()
                ? Collections.emptyList()
                : new ArrayList<>(all.subList((int) from, to)));
        return page;
    }

    /**
     * 按前端传的排序字段构造比较器，默认按 id 倒序（新图片在前）
     */
    private static Comparator<Picture> buildComparator(String sortField, String sortOrder) {
        boolean ascend = "ascend".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder);
        Comparator<Picture> comparator;
        switch (StrUtil.blankToDefault(sortField, "id")) {
            case "createTime":
                comparator = byAsc(Picture::getCreateTime);
                break;
            case "editTime":
                comparator = byAsc(Picture::getEditTime);
                break;
            case "updateTime":
                comparator = byAsc(Picture::getUpdateTime);
                break;
            case "name":
                comparator = byAsc(Picture::getName);
                break;
            case "picSize":
                comparator = byAsc(Picture::getPicSize);
                break;
            case "id":
            default:
                comparator = byAsc(Picture::getId);
                break;
        }
        return ascend ? comparator : comparator.reversed();
    }

    private static <U extends Comparable<? super U>> Comparator<Picture> byAsc(Function<Picture, U> getter) {
        return Comparator.comparing(getter, Comparator.nullsLast(Comparator.<U>naturalOrder()));
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVo(user);
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
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null ,ErrorCode.PARAMS_ERROR);
        if(!space.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //3.查询该空间下所有图片(必须有主色调)
        //动态分表：本条查询路由到 picture_{spaceId}
        List<Picture> pictureList = SpaceTableContext.call(spaceId, () -> this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list());
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
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVo(user));
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
        Picture picture = this.getById(id);
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
        boolean flag = this.updateById(updatePicture);
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
            boolean flag = this.removeById(picture);
            ThrowUtils.throwIf(!flag, ErrorCode.OPERATION_ERROR, "上传失败");
            if(finalSpaceId != null){
                boolean update = spaceService.lambdaUpdate().eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize - " + picture.getPicSize())
                        .setSql("totalCount = totalCount - " + 1)
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR,"更新失败");
            }

            return picture;
        });
        try {
            ((PictureService) AopContext.currentProxy()).delteleObject(picture);
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
            if(!picture.getUserId().equals(id)&& !userService.isAdmin(loginUser)){
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
            Long count = lambdaQuery().eq(Picture::getUrl, url).count();
            if (count > 1) {
                return;
            }
            // FIXME 注意，这里的 url 包含了域名，实际上只要传 key 值（存储路径）就够了
            cosManager.deleteObject(url);
            //清理缩略图
            String thumbnailUrl = oldPicture.getThumbnailUrl();
            if (StrUtil.isNotBlank(thumbnailUrl)) {
                cosManager.deleteObject(thumbnailUrl);
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
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getLoginUser(request);
        //审核
        fillReviewParams(picture, loginUser);
        // 仅本人或管理员可编辑
        //checkPictureAuth(loginUser,oldPicture);
        // 操作数据库
        boolean result = this.updateById(picture);
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

        // 抓取 Bing 图片地址
        List<BingImageFetcher.BingImage> images = bingImageFetcher.fetchImages(searchText, count);

        // 逐条上传入库，单张失败不影响整体；原图失败回退到 Bing 缩略图
        int successCount = 0;
        for (BingImageFetcher.BingImage image : images) {
            PictureUploadRequest uploadRequest = new PictureUploadRequest();
            uploadRequest.setPicName(String.format("%s_%d", namePrefix, successCount + 1));
            boolean uploaded = tryUpload(image.getMurl(), uploadRequest, loginUser);
            if (!uploaded) {
                uploaded = tryUpload(image.getTurl(), uploadRequest, loginUser);
            }
            if (uploaded) {
                successCount++;
            }
        }
        return successCount;
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
            pictureList = this.lambdaQuery()
                    .select(Picture::getId, Picture::getSpaceId, Picture::getName, Picture::getCategory)
                    .isNull(Picture::getSpaceId)
                    .in(Picture::getId, pictureIdList)
                    .list();
        } else {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NO_AUTH_ERROR);
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            pictureList = this.lambdaQuery()
                    .select(Picture::getId, Picture::getSpaceId, Picture::getName, Picture::getCategory)
                    .eq(Picture::getSpaceId, spaceId)
                    .in(Picture::getId, pictureIdList)
                    .list();
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
        boolean result = this.updateBatchById(pictureList);
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
        Picture picture =  Optional.ofNullable(this.getById(pictureId)).orElseThrow(
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
        return aliYunAiApi.createOutPaintingTask(
                picture.getUrl(),
                xScale,
                yScale,
                picture.getPicWidth(),
                picture.getPicHeight(),
                createPictureOutPaintingTaskRequest.getUserPrompt());
    }



    /**
     * 尝试上传单张图片，成功返回 true
     */
    private boolean tryUpload(String url, PictureUploadRequest uploadRequest, User loginUser) {
        if (StrUtil.isBlank(url)) {
            return false;
        }
        try {
            uploadPicture(url, uploadRequest, loginUser);
            return true;
        } catch (Exception e) {
            log.warn("图片抓取上传失败，url = {}", url, e);
            return false;
        }

    }



}




