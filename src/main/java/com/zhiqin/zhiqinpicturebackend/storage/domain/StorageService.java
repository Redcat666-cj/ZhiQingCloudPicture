package com.zhiqin.zhiqinpicturebackend.storage.domain;

import com.zhiqin.zhiqinpicturebackend.storage.domain.UploadPictureResult;

/**
 * 对象存储端口（防腐层）。
 *
 * <p>应用层只关心"把文件/URL 存进去，拿到 url、缩略图、宽高、主色"，
 * 不关心底层是腾讯云 COS 还是别的实现。</p>
 */
public interface StorageService {

    /**
     * 上传图片
     *
     * @param file            本地文件（MultipartFile/File）或网络图片地址（String）
     * @param uploadPathPrefix 存储路径前缀，如 public/{userId}、space/{spaceId}
     */
    UploadPictureResult uploadPicture(Object file, String uploadPathPrefix);

    /**
     * 删除对象（传 url 或 key 都可以，由实现负责解析）
     */
    void deleteObject(String url) throws Exception;
}
