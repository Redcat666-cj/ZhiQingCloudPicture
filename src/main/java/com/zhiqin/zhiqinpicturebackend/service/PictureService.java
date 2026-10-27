package com.zhiqin.zhiqinpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiqin.zhiqinpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.zhiqin.zhiqinpicturebackend.domain.dto.*;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Picture;
import com.zhiqin.zhiqinpicturebackend.domain.vo.PictureVO;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 崔健
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-09-10 19:39:22
*/
public interface PictureService extends IService<Picture> {

    PictureVO uploadPicture(Object file, PictureUploadRequest pictureUploadRequest, User loginUser) throws Exception;



    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest request);

    /**
     * 按请求分页查询单个空间（request.spaceId 为 null 时查公共图库）的图片。
     * 空间由动态表名路由到 picture_public / picture_{spaceId}
     */
    Page<Picture> pageByRequest(PictureQueryRequest request, Page<Picture> page);

    /**
     * 跨空间分页查询（把 picture_public 和各空间表的结果合并后再分页），供管理员全局列表使用
     */
    Page<Picture> pageAcrossSpaces(PictureQueryRequest request);

    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    void validPicture(Picture picture);

    /**
     * 图片审核
     * @param pictureReviewRequest
     * @param user
     */
    void  doPictureReview(PictureReviewRequest pictureReviewRequest,User user);


    void fillReviewParams(Picture picture, User loginUser);

    void editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request) throws Exception;

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

    void delteleObject(Picture oldPicture) throws Exception;

    void checkPictureAuth(User loginUser,Picture picture) throws Exception;

    void deletePicture(User loginUser,Picture picture) throws Exception;

    @Transactional(rollbackFor = Exception.class)
    void editPictureBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) throws Exception;
}



