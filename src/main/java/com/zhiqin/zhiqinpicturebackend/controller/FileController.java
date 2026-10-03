package com.zhiqin.zhiqinpicturebackend.controller;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.utils.IOUtils;
import com.zhiqin.zhiqinpicturebackend.common.BaseResponse;
import com.zhiqin.zhiqinpicturebackend.common.ResultUtils;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.manager.CosManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    private final CosManager cosManager;

    public FileController(CosManager cosManager) {
        this.cosManager = cosManager;
    }

    @PostMapping("/test/upload")
    public BaseResponse<String> upload(@RequestPart("file")MultipartFile file) throws IOException {
        // 文件目录
        String fileName = file.getOriginalFilename();
        String filePath = String.format("/test/%s", fileName);
        // 上传文件
        File tempFile = null;
        try {
            tempFile  = File.createTempFile(filePath, null);
            file.transferTo(tempFile);
            cosManager.putObject(filePath,tempFile);
            return ResultUtils.success(filePath);
        }
        catch (Exception e) {
            log.error("file upload error, filepath = " + filePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
        finally {
            if( tempFile!=null) {
                boolean delete = tempFile.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filePath);
                }
            }





        }

    }

    /*测试文件下载*/
    @GetMapping("/test/downloads/")
    public  void downloads(@RequestParam("fileId") String fileId, HttpServletResponse response) throws Exception {
        byte[] byteArray = null;
        COSObjectInputStream objectContent = null;
        try {
            COSObject object = cosManager.getObject(fileId);
            objectContent = object.getObjectContent();
            byteArray = IOUtils.toByteArray(objectContent);
            response.setContentType("application/octet-stream; charset=utf-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileId);
            //写入响应
            response.getOutputStream().write(byteArray);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            if( objectContent!=null) {
                objectContent.close();
            }
        }


    }

}
