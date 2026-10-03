package com.zhiqin.zhiqinpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class FilePictureUpload extends PictureUploadTemplate{
    @Override
    protected void ProcessFile(Object file, File tempFile) throws IOException {
        MultipartFile multipartFile = (MultipartFile) file;
        multipartFile.transferTo(tempFile);
    }

    @Override
    protected void vaildPicture(Object file) {
        MultipartFile multipartFile = (MultipartFile) file;
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 1. 校验文件大小
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
        // 2. 校验文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
    }

    @Override
    protected String getOriginalFilename(Object file) {
        MultipartFile multipartFile = (MultipartFile) file;
        return multipartFile.getOriginalFilename();
    }
}
