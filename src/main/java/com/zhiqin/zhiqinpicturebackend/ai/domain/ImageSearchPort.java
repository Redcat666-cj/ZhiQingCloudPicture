package com.zhiqin.zhiqinpicturebackend.ai.domain;

import com.zhiqin.zhiqinpicturebackend.ai.domain.dto.ImageSearchResult;

import java.util.List;

/**
 * 以图搜图端口（防腐层），实现见 {@code ai.infrastructure.ImageSearchApiFacede}。
 */
public interface ImageSearchPort {

    /**
     * 按图片地址搜索相似图片
     */
    List<ImageSearchResult> searchImage(String imageUrl);
}
