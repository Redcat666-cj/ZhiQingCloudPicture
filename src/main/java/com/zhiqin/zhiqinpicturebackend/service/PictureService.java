package com.zhiqin.zhiqinpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiqin.zhiqinpicturebackend.domain.dto.PictureQueryRequest;
import com.zhiqin.zhiqinpicturebackend.domain.dto.PictureReviewRequest;
import com.zhiqin.zhiqinpicturebackend.domain.dto.PictureUploadByBatchRequest;
import com.zhiqin.zhiqinpicturebackend.domain.dto.PictureUploadRequest;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Picture;
import com.zhiqin.zhiqinpicturebackend.domain.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author 崔健
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-09-10 19:39:22
*/
public interface PictureService extends IService<Picture> {

    PictureVO uploadPicture(Object file, PictureUploadRequest pictureUploadRequest,User loginUser) throws Exception;



    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest request);

    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    void validPicture(Picture picture);

    /**
     * 图片审核
     * @param pictureReviewRequest
     * @param user
     */
    void  doPictureReview(PictureReviewRequest pictureReviewRequest,User user);


    void fillReviewParams(Picture picture, User loginUser);

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
}



