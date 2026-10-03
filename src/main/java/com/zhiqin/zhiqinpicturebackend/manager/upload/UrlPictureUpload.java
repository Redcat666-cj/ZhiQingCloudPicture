package com.zhiqin.zhiqinpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class UrlPictureUpload extends PictureUploadTemplate{
    @Override
    protected void ProcessFile(Object file, File tempFile) throws IOException {
        String fileUrl = (String) file;
        // 下载文件到临时目录，携带浏览器 UA 与 Referer，规避部分图源防盗链返回 567
        HttpResponse response = HttpRequest.get(fileUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", fileUrl)
                .execute();
        if (response.getStatus() != HttpStatus.HTTP_OK) {
            throw new IOException("图片下载失败，HTTP 状态码：" + response.getStatus());
        }
        response.writeBody(tempFile);
    }
    @Override
    protected void vaildPicture(Object file) {
        String fileUrl = (String) file;
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");
        //校验url协议
        ThrowUtils.throwIf(!(fileUrl.startsWith("http://") || fileUrl.startsWith("https://")),
                ErrorCode.PARAMS_ERROR, "仅支持HTTP或HTTPS协议的文件地址");
        //发送head请求验证文件是否存在
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
        } catch (Exception e) {
            //有些 URL 地址可能不支持通过 HEAD 请求访问，为了提高导入成功率，即使 HEAD 请求访问失败，也不会报错，并且不用执行后续的校验。仅对能获取到的信息进行校验。
            return;
        }
        try {
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            //文件存在校验格式
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型（Content-Type 可能携带 charset，需截断）
                String mimeType = contentType.toLowerCase().split(";")[0].trim();
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg",
                        "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(mimeType), ErrorCode.PARAMS_ERROR, "文件类型错误");
                //文件存在校验大小
                String contentLengthStr = response.header("Content-Length");
                if (StrUtil.isNotBlank(contentLengthStr)) {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long ONE_MB = 1024 * 1024;
                    ThrowUtils.throwIf(contentLength > 3 * ONE_MB, ErrorCode.PARAMS_ERROR, "文件大小不能超过3MB");

                }
            }
        } finally {
            //发送http请求后记得关闭避免占用资源
            response.close();
        }
    }

    @Override
    protected String getOriginalFilename(Object file) {
        String fileUrl = (String) file;
        // 从 URL 中提取文件名
        return FileUtil.mainName(fileUrl);
    }
}
