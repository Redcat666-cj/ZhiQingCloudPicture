package com.zhiqin.zhiqinpicturebackend.manager;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.zhiqin.zhiqinpicturebackend.common.ResultUtils;
import com.zhiqin.zhiqinpicturebackend.config.CosConfig;
import com.zhiqin.zhiqinpicturebackend.domain.dto.UploadPictureResult;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
/*
*已废弃改为upload模版优化
*/
@Slf4j
@Service
@Deprecated
public class FileManager {


    @Resource
    private CosConfig cosConfig;

    @Resource
    private CosManager cosManager;





public UploadPictureResult  uploadPicture(MultipartFile file,String uploadPathPrefix){
     //校验图片
    vaildPicture(file);
    //图片上传地址
    String uuid = RandomUtil.randomString(16);
    String originalFilename = file.getOriginalFilename();
    //自己拼接不是使用原始名称增加安全性
    String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),uuid,
            FileUtil.getSuffix(originalFilename));
    String uploadPath = String.format("%s/%s",uploadPathPrefix,uploadFileName);
    // 上传文件
    File tempFile = null;
    try {
        tempFile  = File.createTempFile(uploadPath, null);
        file.transferTo(tempFile);
        PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, tempFile);

        // cosManager.putObject(uploadPath,tempFile);
        //获取图片信息对象
        ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
        // 封装返回结果
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
    catch (Exception e) {
        log.error("图片上传对象存储失败 " + uploadPath, e);
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
    }
    finally {
        deleteTempFile(tempFile);

    }

}

private void vaildPicture(MultipartFile file) {
        ThrowUtils.throwIf(file == null, ErrorCode.PARAMS_ERROR,"文件不存在");
        //1.校验文件大小
        long size = file.getSize();
        final long ONE_MB = 1024 * 1024;
        ThrowUtils.throwIf(size > 3 * ONE_MB, ErrorCode.PARAMS_ERROR,"文件大小不能超过3MB");
        //校验文件后缀
        String suffix = FileUtil.getSuffix(file.getOriginalFilename());
        //允许文件上传的后缀列表
        final List<String> ALLOW_LIST = Arrays.asList("jpeg" , "png","jpg","gif","bmp","webp");
        ThrowUtils.throwIf(!ALLOW_LIST.contains(suffix),ErrorCode.PARAMS_ERROR,"文件类型错误");



}

    public static void deleteTempFile(File tempFile) {
        if (tempFile != null) {
            boolean deleteResult = tempFile.delete();
            if (!deleteResult) {
                log.error("file delete error, filepath = {}", tempFile.getAbsolutePath());
            }
        }
    }

    public UploadPictureResult  uploadPictureByUrl(String fileUrl,String uploadPathPrefix){
        //校验图片
        vaildPicture(fileUrl);
        //图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originalFilename = FileUtil.mainName(fileUrl);
        //String originalFilename = file.getOriginalFilename();
        //自己拼接不是使用原始名称增加安全性
        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),uuid,
                FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("%s/%s",uploadPathPrefix,uploadFileName);
        // 上传文件
        File tempFile = null;
        try {
            tempFile  = File.createTempFile(uploadPath, null);
            HttpUtil.downloadFile(fileUrl, tempFile);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, tempFile);

            // cosManager.putObject(uploadPath,tempFile);
            //获取图片信息对象
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
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
        catch (Exception e) {
            log.error("图片上传对象存储失败 " + uploadPath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
        finally {
            deleteTempFile(tempFile);

        }

    }

    private void vaildPicture(String fileUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl),ErrorCode.PARAMS_ERROR,"文件地址不能为空");

        try {
            //校验url格式
            new URL(fileUrl); //验证是否合法Url
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件地址格式不正确");
        }
        //校验url协议
          ThrowUtils.throwIf(!(fileUrl.startsWith("http://")||fileUrl.startsWith("https://")),
                  ErrorCode.PARAMS_ERROR,"仅支持HTTP或HTTPS协议的文件地址");
        //发送head请求验证文件是否存在
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
        } catch (Exception e) {
            //有些 URL 地址可能不支持通过 HEAD 请求访问，为了提高导入成功率，即使 HEAD 请求访问失败，也不会报错，并且不用执行后续的校验。仅对能获取到的信息进行校验。
            return;
        }
        try {
            if (response.getStatus()!= HttpStatus.HTTP_OK) {
                return;
            }
            //文件存在校验格式
            String contentType = response.header("Content-Type");
            if(StrUtil.isNotBlank(contentType)){
                // 允许的图片类型（Content-Type 可能携带 charset，需截断）
                String mimeType = contentType.toLowerCase().split(";")[0].trim();
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg",
                        "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(mimeType),ErrorCode.PARAMS_ERROR,"文件类型错误");
                //文件存在校验大小
                String contentLengthStr = response.header("Content-Length");
                if(StrUtil.isNotBlank(contentLengthStr)){
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long ONE_MB = 1024 * 1024;
                    ThrowUtils.throwIf(contentLength > 3 * ONE_MB, ErrorCode.PARAMS_ERROR,"文件大小不能超过3MB");

                }
            }
        }
        finally  {
            //发送http请求后记得关闭避免占用资源
            if(response != null) {
                response.close();
            }
        }






    }


}