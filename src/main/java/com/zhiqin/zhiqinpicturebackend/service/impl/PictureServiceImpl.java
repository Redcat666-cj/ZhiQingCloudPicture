package com.zhiqin.zhiqinpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhiqin.zhiqinpicturebackend.constant.UserConstant;
import com.zhiqin.zhiqinpicturebackend.domain.dto.*;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Picture;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.domain.enums.PictureReviewStatusEnum;
import com.zhiqin.zhiqinpicturebackend.domain.vo.PictureVO;
import com.zhiqin.zhiqinpicturebackend.domain.vo.UserVO;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.exception.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.manager.BingImageFetcher;
import com.zhiqin.zhiqinpicturebackend.manager.CosManager;
import com.zhiqin.zhiqinpicturebackend.manager.FileManager;
import com.zhiqin.zhiqinpicturebackend.manager.upload.FilePictureUpload;
import com.zhiqin.zhiqinpicturebackend.manager.upload.PictureUploadTemplate;
import com.zhiqin.zhiqinpicturebackend.manager.upload.UrlPictureUpload;
import com.zhiqin.zhiqinpicturebackend.service.PictureService;
import com.zhiqin.zhiqinpicturebackend.mapper.PictureMapper;
import com.zhiqin.zhiqinpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @Autowired
    private CosManager cosManager;

    @Override
    public PictureVO uploadPicture(Object file, PictureUploadRequest pictureUploadRequest, User loginUser) throws Exception {
        if(file == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片为空");
        }

        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR);
        //判断是新增还是删除
        Long pictureId = null;
        String picName = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
            picName = pictureUploadRequest.getPicName();
        }
        //如果是更新判断图片是否存在
        if (!(pictureId == null)) {
            boolean exists = this.lambdaQuery().eq(Picture::getId, pictureId)
                    .exists();
            ThrowUtils.throwIf(exists, ErrorCode.PARAMS_ERROR, "图片不存在");

        }
        //上传图片，得到图片信息
        // 按照用户 id 划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        // 根据 file 类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if(file instanceof String){
            pictureUploadTemplate = urlPictureUpload;

        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(file, uploadPathPrefix);

        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 名称优先使用传入的自定义名称，否则使用原始文件名
        picture.setName(StrUtil.isNotBlank(picName) ? picName : uploadPictureResult.getPicName());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        //审核
        this.fillReviewParams(picture, loginUser);
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        boolean flag = saveOrUpdate(picture);
        ThrowUtils.throwIf(!flag, ErrorCode.OPERATION_ERROR, "上传失败");

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
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 从多字段中搜索
        if (StrUtil.isNotEmpty(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText).or()
                    .like("introduction", searchText));
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
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
    @Async //记得在启动类打上enable 一般不建议使用默认线程池
    @Override
    public void delteleObject(Picture oldPicture) throws Exception {
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




