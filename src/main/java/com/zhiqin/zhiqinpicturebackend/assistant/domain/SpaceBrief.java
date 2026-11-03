package com.zhiqin.zhiqinpicturebackend.assistant.domain;

import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceTypeEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 空间摘要（值对象）：AI 助手向模型介绍"用户能访问哪些空间"时使用的最小结构。
 */
@Data
public class SpaceBrief implements Serializable {

    private String id;

    private String spaceName;

    /**
     * private / team
     */
    private String spaceType;

    /**
     * 空间级别文本：普通版 / 专业版 / 旗舰版
     */
    private String spaceLevel;

    /**
     * 当前图片数量
     */
    private Long totalCount;

    private static final long serialVersionUID = 1L;

    public static SpaceBrief from(Space space) {
        SpaceBrief brief = new SpaceBrief();
        if (space == null) {
            return brief;
        }
        brief.setId(space.getId() == null ? null : String.valueOf(space.getId()));
        brief.setSpaceName(space.getSpaceName());
        if (space.getSpaceType() != null && space.getSpaceType() == SpaceTypeEnum.TEAM.getValue()) {
            brief.setSpaceType("team");
        } else if (space.getSpaceType() != null && space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            brief.setSpaceType("private");
        }
        brief.setSpaceLevel(SpaceLevelText.textOf(space.getSpaceLevel()));
        brief.setTotalCount(space.getTotalCount());
        return brief;
    }

    /**
     * 空间级别的展示文本。
     */
    private static class SpaceLevelText {
        private static String textOf(Integer level) {
            if (level == null) {
                return "普通版";
            }
            return switch (level) {
                case 1 -> "专业版";
                case 2 -> "旗舰版";
                default -> "普通版";
            };
        }
    }

    public static List<SpaceBrief> fromList(List<Space> spaces) {
        List<SpaceBrief> list = new ArrayList<>();
        if (spaces == null) {
            return list;
        }
        for (Space space : spaces) {
            list.add(from(space));
        }
        return list;
    }
}
