package com.zhiqin.zhiqinpicturebackend.api.imagesearch.sub;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.api.imagesearch.model.ImageSearchResult;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GetImageListApi {

    /**
     * 获取图片列表
     *
     * @param url
     * @return
     */
    public static List<ImageSearchResult> getImageList(String url) {
        try {
            // 发起GET请求
            HttpResponse response = HttpUtil.createGet(url).execute();

            // 获取响应内容
            int statusCode = response.getStatus();
            String body = response.body();

            // 处理响应
            if (statusCode == 200) {
                // 解析 JSON 数据并处理
                return processResponse(body);
            } else {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取图片列表失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片列表失败");
        }
    }

    /**
     * 处理接口响应内容
     *
     * @param responseBody 接口返回的JSON字符串
     */
    private static List<ImageSearchResult> processResponse(String responseBody) {
        // 解析响应对象
        JSONObject jsonObject = new JSONObject(responseBody);
        if (!jsonObject.containsKey("data")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表");
        }
        //data 可能是 JSON null，此时 getJSONObject 返回 null
        JSONObject data = jsonObject.getJSONObject("data");
        if (data == null || !data.containsKey("list")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表：" + responseBody);
        }
        JSONArray list = data.getJSONArray("list");
        return JSONUtil.toList(list, ImageSearchResult.class);
    }

    public static void main(String[] args) {
        //session_id / sign / tk 每次搜索都会变，必须实时走一遍前两步获取，不能写死
        String imageUrl = "https://ts1.tc.mm.bing.net/th/id/OIP-C.U_uj1lJJzLqyAf1WljIAVAHaE2?r=0&rs=1&pid=ImgDetMain&o=7&rm=3";
        String pageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String firstUrl = GetImageFirstUrlApi.getImageFirstUrl(pageUrl);
        List<ImageSearchResult> imageList = getImageList(firstUrl);
        System.out.println("搜索成功" + imageList);
    }
}
