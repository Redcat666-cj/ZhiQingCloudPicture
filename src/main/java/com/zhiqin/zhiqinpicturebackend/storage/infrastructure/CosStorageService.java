package com.zhiqin.zhiqinpicturebackend.storage.infrastructure;

import com.qcloud.cos.model.StorageClass;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.storage.domain.UploadPictureResult;
import com.zhiqin.zhiqinpicturebackend.storage.domain.StorageService;
import com.zhiqin.zhiqinpicturebackend.storage.infrastructure.upload.BytesPictureUpload;
import com.zhiqin.zhiqinpicturebackend.storage.infrastructure.upload.FilePictureUpload;
import com.zhiqin.zhiqinpicturebackend.storage.infrastructure.upload.PictureUploadTemplate;
import com.zhiqin.zhiqinpicturebackend.storage.infrastructure.upload.UrlPictureUpload;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 对象存储端口实现：腾讯云 COS。
 *
 * <p>按入参类型分派到三条上传模板：String → URL 图片、byte[] → 内存字节（批量抓取已
 * 下载做指纹判重的场景，避免二次下载）、其余 → 本地文件（MultipartFile）；
 * 真正的 CI 处理（压缩、缩略图、元数据）在模板里，这里只做端口适配。</p>
 */
@Service
public class CosStorageService implements StorageService {

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private BytesPictureUpload bytesPictureUpload;

    @Resource
    private CosManager cosManager;

    @Override
    public UploadPictureResult uploadPicture(Object file, String uploadPathPrefix) {
        PictureUploadTemplate template;
        if (file instanceof String) {
            template = urlPictureUpload;
        } else if (file instanceof byte[]) {
            template = bytesPictureUpload;
        } else {
            template = filePictureUpload;
        }
        return template.uploadPicture(file, uploadPathPrefix);
    }

    @Override
    public void deleteObject(String url) throws Exception {
        cosManager.deleteObject(url);
    }

    @Override
    public void demoteToLowFrequency(String url) throws Exception {
        changeStorageClass(url, StorageClass.Standard_IA);
    }

    @Override
    public void restoreToStandard(String url) throws Exception {
        changeStorageClass(url, StorageClass.Standard);
    }

    /**
     * 自拷贝改存储类：同桶同 key，元数据与访问地址不变，仅存储层级变化。
     * 若对象已是目标层级（DB 标记与实际错位时会发生），跳过 COS 调用做幂等保护。
     */
    private void changeStorageClass(String url, StorageClass storageClass) throws Exception {
        String key = extractKey(url);
        StorageClass current = cosManager.getObjectStorageClass(key);
        if (current == storageClass) {
            return;
        }
        cosManager.changeObjectStorageClass(key, storageClass);
    }

    /**
     * 从 COS 访问 URL 中提取对象 key：取 ".myqcloud.com/" 之后的部分。
     */
    private String extractKey(String url) {
        String marker = ".myqcloud.com/";
        int idx = url.indexOf(marker);
        ThrowUtils.throwIf(idx < 0, ErrorCode.SYSTEM_ERROR, "非 COS 地址，无法变更存储类：" + url);
        return url.substring(idx + marker.length());
    }
}
