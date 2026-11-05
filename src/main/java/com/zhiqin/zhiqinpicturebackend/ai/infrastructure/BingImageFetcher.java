package com.zhiqin.zhiqinpicturebackend.ai.infrastructure;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * 同一次抓取内按规范化 URL 去重（Bing 单页结果常含重复图）。
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
        Set<String> seenUrls = new HashSet<>();
        for (Element element : imageElements) {
            if (images.size() >= count) {
                break;
            }
            BingImage image = parseImage(element);
            if (image == null) {
                continue;
            }
            String normalized = normalizeUrl(image.getMurl());
            if (normalized == null || !seenUrls.add(normalized)) {
                continue;
            }
            images.add(image);
        }
        return images;
    }

    /**
     * URL 规范化：去掉协议差异与常见跟踪参数，降低“同一张图不同参数”造成的漏判。
     * 无法解析的 URL 返回 null（按无效处理，交由上层回退 turl）。
     */
    private String normalizeUrl(String rawUrl) {
        if (StrUtil.isBlank(rawUrl)) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase();
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (host.isEmpty()) {
                return null;
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            // 剔除跟踪/尺寸类参数，只保留可能影响内容的参数
            StringBuilder query = new StringBuilder();
            if (uri.getRawQuery() != null) {
                String[] pairs = uri.getRawQuery().split("&");
                Arrays.sort(pairs);
                for (String pair : pairs) {
                    String key = pair.split("=")[0].toLowerCase();
                    if (IGNORED_QUERY_KEYS.contains(key)) {
                        continue;
                    }
                    if (!query.isEmpty()) {
                        query.append('&');
                    }
                    query.append(pair);
                }
            }
            String normalized = scheme + "://" + host + path + (query.isEmpty() ? "" : "?" + query);
            return normalized;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** URL 中不影响图片内容的参数，规范化时剔除 */
    private static final Set<String> IGNORED_QUERY_KEYS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "ref", "referer", "from", "w", "h", "width", "height", "q", "size");

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
