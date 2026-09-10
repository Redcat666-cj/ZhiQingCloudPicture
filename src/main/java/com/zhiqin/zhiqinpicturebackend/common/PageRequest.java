package com.zhiqin.zhiqinpicturebackend.common;

import lombok.Data;
/*分页请求包装类，接受页号、页面大小、排序字段、排序顺‌序参数‌：*/
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = "descend";
}
