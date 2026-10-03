package com.zhiqin.zhiqinpicturebackend.manager;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Bing 图片抓取器
 */
@Slf4j
@Component
public class BingImageFetcher {

    private static final String BING_IMAGE_URL = "https://cn.bing.com/images/async";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 根据搜索词抓取 Bing 图片。
     * 每张图片同时返回原图地址 murl 与 Bing 缩略图地址 turl，
     * 用于原图因防盗链下载失败时回退到缩略图。
     *
     * @param searchText 搜索词
     * @param count      抓取数量
     * @return 图片信息列表
     */
    public List<BingImage> fetchImages(String searchText, int count) {
        String url = buildUrl(searchText);
        Document document = fetchHtml(url);
        Elements imageElements = document.select("a.iusc");
        List<BingImage> images = new ArrayList<>();
        for (Element element : imageElements) {
            if (images.size() >= count) {
                break;
            }
            BingImage image = parseImage(element);
            if (image != null) {
                images.add(image);
            }
        }
        return images;
    }

    /**
     * 构建请求地址
     */
    private String buildUrl(String searchText) {
        String encoded = URLEncoder.encode(searchText, StandardCharsets.UTF_8);
        return String.format("%s?q=%s&mmasync=1", BING_IMAGE_URL, encoded);
    }

    /**
     * 抓取 HTML 文档
     */
    private Document fetchHtml(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Referer", "https://cn.bing.com/images")
                    .timeout(10000)
                    .get();
        } catch (IOException e) {
            log.error("抓取 Bing 图片失败，url = {}", url, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "抓取图片失败");
        }
    }

    /**
     * 从图片元素中解析图片地址。
     * Bing 将图片信息以 JSON 形式编码在 a.iusc 元素的 m 属性中，
     * murl 为原图地址，turl 为 Bing 缩略图地址。
     */
    private BingImage parseImage(Element element) {
        String m = element.attr("m");
        if (StrUtil.isBlank(m)) {
            return null;
        }
        try {
            JSONObject jsonObject = JSONUtil.parseObj(m);
            BingImage image = new BingImage();
            image.setMurl(jsonObject.getStr("murl"));
            image.setTurl(jsonObject.getStr("turl"));
            return image;
        } catch (Exception e) {
            log.error("解析图片 m 属性失败，m = {}", m, e);
            return null;
        }
    }

    /**
     * Bing 图片信息
     */
    @Data
    public static class BingImage {
        /** 原图地址 */
        private String murl;
        /** Bing 缩略图地址 */
        private String turl;
    }
}
