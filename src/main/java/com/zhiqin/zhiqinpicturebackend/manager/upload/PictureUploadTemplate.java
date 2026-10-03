package com.zhiqin.zhiqinpicturebackend.manager.upload;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.zhiqin.zhiqinpicturebackend.config.CosConfig;
import com.zhiqin.zhiqinpicturebackend.domain.dto.UploadPictureResult;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.manager.CosManager;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class PictureUploadTemplate {


    @Resource
    protected CosConfig cosConfig;

    @Resource
    protected CosManager cosManager;




/*模版方法定义上传流程*/
public final UploadPictureResult  uploadPicture(Object file,String uploadPathPrefix){
    //校验图片
    vaildPicture(file);
    //图片上传地址
    String uuid = RandomUtil.randomString(16);
    String originalFilename = getOriginalFilename(file);
    //自己拼接不是使用原始名称增加安全性
    String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),uuid,
            FileUtil.getSuffix(originalFilename));
    String uploadPath = String.format("%s/%s",uploadPathPrefix,uploadFileName);
    // 上传文件
    File tempFile = null;
    try {
        //获取临时文件
        tempFile  = File.createTempFile(uploadPath, null);
        // 处理文件来源（本地或 URL）
        ProcessFile(file,tempFile);
        PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, tempFile);

        // cosManager.putObject(uploadPath,tempFile);
        //获取图片信息对象
        ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
        ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
        // 未做图片处理时(如小图跳过压缩)不会返回 ProcessResults,直接走原图返回
        List<CIObject> objectList = processResults == null ? null : processResults.getObjectList();
        if(CollUtil.isNotEmpty(objectList)){
            // COS 返回的结果顺序与规则添加顺序不保证一致，按 key 区分压缩图与缩略图
            CIObject compressCiObject = null;
            CIObject thunmnailCiObject = null;
            for (CIObject object : objectList) {
                if (object.getKey() != null && object.getKey().contains(".thumbnail")) {
                    thunmnailCiObject = object;
                } else {
                    compressCiObject = object;
                }
            }
            // 兜底：缺少某一项时回退到另一项
            if (compressCiObject == null) {
                compressCiObject = thunmnailCiObject;
            }
            if (thunmnailCiObject == null) {
                thunmnailCiObject = compressCiObject;
            }

            //封装压缩图返回
            return buildResult(originalFilename,compressCiObject,thunmnailCiObject);
        }

        // 封装返回结果
        return getUploadPictureResult(imageInfo, originalFilename, tempFile, uploadPath);
    }
    catch (Exception e) {
        log.error("图片上传对象存储失败 " + uploadPath, e);
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
    }
    finally {
        deleteTempFile(tempFile);

    }

}

    private UploadPictureResult buildResult(String originalFilename, CIObject compressCiObject,CIObject thunmnailCiObject) {
    UploadPictureResult uploadPictureResult = new UploadPictureResult();
    Integer width = compressCiObject.getWidth();
    Integer height = compressCiObject.getHeight();
    double picScale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();
    uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
    uploadPictureResult.setPicWidth(width);
    uploadPictureResult.setPicHeight(height);
    uploadPictureResult.setPicScale(picScale);
    uploadPictureResult.setPicFormat(compressCiObject.getFormat());
    uploadPictureResult.setPicSize(compressCiObject.getSize().longValue());
    uploadPictureResult.setUrl(cosConfig.getHost()+"/"+compressCiObject.getKey());
    uploadPictureResult.setThumbnailUrl(cosConfig.getHost()+"/"+thunmnailCiObject.getKey());
    return uploadPictureResult;
    }



    private @NonNull UploadPictureResult getUploadPictureResult(ImageInfo imageInfo, String originalFilename, File tempFile, String uploadPath) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int width = imageInfo.getWidth();
        int height = imageInfo.getHeight();
        double pictureScale = NumberUtil.round(width*1.0/ height,2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicWidth(width);
        uploadPictureResult.setPicHeight(height);
        uploadPictureResult.setPicScale(pictureScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicSize(FileUtil.size(tempFile));
        uploadPictureResult.setUrl(cosConfig.getHost() + "/" + uploadPath);
        return uploadPictureResult;
    }

    /**
     * 处理输入源并生成本地临时文件
     */
    protected abstract void ProcessFile(Object file, File tempFile) throws IOException;

    /*校验输入源(本地或者URL)*/
    protected abstract void vaildPicture(Object file) ;
    /*获取输入源的原始图片*/
    protected abstract  String getOriginalFilename(Object file) ;




    public static void deleteTempFile(File tempFile) {
        if (tempFile != null) {
            boolean deleteResult = tempFile.delete();
            if (!deleteResult) {
                log.error("file delete error, filepath = {}", tempFile.getAbsolutePath());
            }
        }
    }




}