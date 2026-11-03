package com.zhiqin.zhiqinpicturebackend.storage.infrastructure;

import com.zhiqin.zhiqinpicturebackend.storage.domain.UploadPictureResult;
import com.zhiqin.zhiqinpicturebackend.storage.domain.StorageService;
import com.zhiqin.zhiqinpicturebackend.storage.infrastructure.upload.FilePictureUpload;
import com.zhiqin.zhiqinpicturebackend.storage.infrastructure.upload.PictureUploadTemplate;
import com.zhiqin.zhiqinpicturebackend.storage.infrastructure.upload.UrlPictureUpload;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 对象存储端口实现：腾讯云 COS。
 *
 * <p>按入参类型分派到本地文件 / 网络图片两条上传模板；真正的 CI 处理（压缩、缩略图、元数据）
 * 仍在两个模板里，这里只做端口适配。</p>
 */
@Service
public class CosStorageService implements StorageService {

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private CosManager cosManager;

    @Override
    public UploadPictureResult uploadPicture(Object file, String uploadPathPrefix) {
        PictureUploadTemplate template = file instanceof String ? urlPictureUpload : filePictureUpload;
        return template.uploadPicture(file, uploadPathPrefix);
    }

    @Override
    public void deleteObject(String url) throws Exception {
        cosManager.deleteObject(url);
    }
}
