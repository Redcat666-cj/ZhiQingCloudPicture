package com.zhiqin.zhiqinpicturebackend.ai.infrastructure;

import com.zhiqin.zhiqinpicturebackend.ai.domain.ImageSearchPort;
import org.springframework.stereotype.Component;

import com.zhiqin.zhiqinpicturebackend.ai.domain.dto.ImageSearchResult;
import com.zhiqin.zhiqinpicturebackend.ai.infrastructure.imagesearch.GetImageFirstUrlApi;
import com.zhiqin.zhiqinpicturebackend.ai.infrastructure.imagesearch.GetImageListApi;
import com.zhiqin.zhiqinpicturebackend.ai.infrastructure.imagesearch.GetImagePageUrlApi;


import java.util.List;

@Component
public class ImageSearchApiFacede implements ImageSearchPort {

    @Override
    public List<ImageSearchResult> searchImage(String imageUrl) {
        return searchImageStatic(imageUrl);
    }

    /**
     * 搜索图片
     *
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImageStatic(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;

    }
    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://ts1.tc.mm.bing.net/th/id/OIP-C.U_uj1lJJzLqyAf1WljIAVAHaE2?r=0&rs=1&pid=ImgDetMain&o=7&rm=3";
        List<ImageSearchResult> resultList = searchImageStatic(imageUrl);
        System.out.println("结果列表" + resultList);
    }
}
