package com.zhiqin.zhiqinpicturebackend.api.imagesearch.sub;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 获取以图搜图页面地址 <step 1></>
 */
@Slf4j
public class GetImagePageUrlApi {
    /**
     * 获取图片页面地址
     * @param imageUrl
     * @return
     */
    public static String getImagePageUrl(String imageUrl) {
        //1.请求参数准备
        Map<String,Object> formData = new HashMap<>();
        formData.put("image",imageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        //获取当前时间戳
        long uptime =  System.currentTimeMillis();
        //请求地址
        String Url = "https://graph.baidu.com/upload?uptime=" + uptime;
        try{
            //2.发送Post请求到百度接口
            HttpResponse response = HttpRequest.post(Url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    //百度网关要求携带非空的 Acs-Token，否则直接返回 {"status":1,"msg":"Reject"}
                    .header("Acs-Token", "0")
                    .form(formData)
                    .timeout(5000)
                    .execute();
            //判断响应状态
            if(HttpStatus.HTTP_OK!=response.getStatus()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            //解析响应
            String responseBody = response.body();
            Map<String,Object> result = JSONUtil.toBean(responseBody, Map.class);

            //3.处理响应结果
            if(result==null||!Integer.valueOf(0).equals(result.get("status"))){
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败：" + responseBody);
            }
            Map<String,Object> data = (Map<String, Object>) result.get("data");
            String rawUrl = (String)data.get("url");
            //对Url进行解码
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            //如果Url为空
            if(StrUtil.isEmpty(searchResultUrl)){
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效结果");
            }
            return searchResultUrl;
        }catch (BusinessException e){
            //业务异常按原样抛出，避免真实失败原因被覆盖成笼统的"搜索失败"
            throw e;
        }catch (Exception e){
            log.error("搜索失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }



    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://ts1.tc.mm.bing.net/th/id/OIP-C.U_uj1lJJzLqyAf1WljIAVAHaE2?r=0&rs=1&pid=ImgDetMain&o=7&rm=3";
        String result = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + result);
    }
}


