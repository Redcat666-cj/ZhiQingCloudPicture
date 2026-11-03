package com.zhiqin.zhiqinpicturebackend.shared.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果（领域/应用层通用），与 MyBatis-Plus 的 Page 解耦。
 *
 * <p>基础设施层负责把 {@code IPage} 转成它，领域层只看到这个朴素对象。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据
     */
    private List<T> records;

    /**
     * 总条数
     */
    private long total;

    /**
     * 当前页码（从 1 开始）
     */
    private long current;

    /**
     * 每页条数
     */
    private long size;

    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(Collections.emptyList(), 0L, current, size);
    }

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        return new PageResult<>(records, total, current, size);
    }
}
