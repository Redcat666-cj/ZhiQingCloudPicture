package com.zhiqin.zhiqinpicturebackend.api.imagesearch;

import com.zhiqin.zhiqinpicturebackend.api.imagesearch.model.ImageSearchResult;
import com.zhiqin.zhiqinpicturebackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.zhiqin.zhiqinpicturebackend.api.imagesearch.sub.GetImageListApi;
import com.zhiqin.zhiqinpicturebackend.api.imagesearch.sub.GetImagePageUrlApi;


import java.util.List;

public class ImageSearchApiFacede {

    /**
     * 搜索图片
     *
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;

    }
    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://ts1.tc.mm.bing.net/th/id/OIP-C.U_uj1lJJzLqyAf1WljIAVAHaE2?r=0&rs=1&pid=ImgDetMain&o=7&rm=3";
        List<ImageSearchResult> resultList = searchImage(imageUrl);
        System.out.println("结果列表" + resultList);
    }
}
