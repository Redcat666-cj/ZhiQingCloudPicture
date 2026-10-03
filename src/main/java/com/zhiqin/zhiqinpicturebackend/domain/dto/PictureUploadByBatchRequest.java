package com.zhiqin.zhiqinpicturebackend.domain.dto;

import lombok.Data;

@Data
public class PictureUploadByBatchRequest {  
  
    /**  
     * 搜索词  
     */  
    private String searchText;  
  
    /**
     * 抓取数量
     */
    private Integer count = 10;

    /**
     * 名称前缀（用于统一命名，为空则回退到搜索词）
     */
    private String namePrefix;
}
