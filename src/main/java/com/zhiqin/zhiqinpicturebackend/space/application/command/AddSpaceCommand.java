package com.zhiqin.zhiqinpicturebackend.space.application.command;

import lombok.Data;

/**
 * 创建空间命令
 */
@Data
public class AddSpaceCommand {

    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;
}
