package com.zhiqin.zhiqinpicturebackend.assistant.domain;

import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.picture.domain.Picture;
import lombok.Data;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片摘要（值对象）。
 *
 * <p>AI 助手工具返回给模型、也随 SSE 推给前端的最小图片结构：
 * 雪花 id 转字符串避免模型/前端精度丢失，tags 解析成列表便于模型理解。</p>
 */
@Data
public class PictureBrief implements Serializable {

    private String id;

    private String name;

    private String introduction;

    private String thumbnailUrl;

    private String url;

    private List<String> tags = new ArrayList<>();

    private String category;

    private String spaceId;

    private String createTime;

    private static final long serialVersionUID = 1L;

    public static PictureBrief from(Picture picture) {
        PictureBrief brief = new PictureBrief();
        if (picture == null) {
            return brief;
        }
        brief.setId(picture.getId() == null ? null : String.valueOf(picture.getId()));
        brief.setName(picture.getName());
        brief.setIntroduction(picture.getIntroduction());
        brief.setThumbnailUrl(picture.getThumbnailUrl());
        brief.setUrl(picture.getUrl());
        brief.setTags(parseTags(picture.getTags()));
        brief.setCategory(picture.getCategory());
        brief.setSpaceId(picture.getSpaceId() == null ? null : String.valueOf(picture.getSpaceId()));
        if (picture.getCreateTime() != null) {
            brief.setCreateTime(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(picture.getCreateTime()));
        }
        return brief;
    }

    private static List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(tagsJson, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
