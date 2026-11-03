package com.zhiqin.zhiqinpicturebackend.space.domain;

import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;

/**
 * 空间配额值对象：把 maxSize/maxCount/totalSize/totalCount 四个字段收成一个不可变对象。
 *
 * <p>原来这些判断散在 PictureAppServiceImpl 里（"空间条数不足"/"空间不足"），现在只有这里一处。</p>
 */
public final class SpaceQuota {

    private final long maxSize;

    private final long maxCount;

    private final long usedSize;

    private final long usedCount;

    public SpaceQuota(Long maxSize, Long maxCount, Long totalSize, Long totalCount) {
        this.maxSize = nz(maxSize);
        this.maxCount = nz(maxCount);
        this.usedSize = nz(totalSize);
        this.usedCount = nz(totalCount);
    }

    public long getMaxSize() {
        return maxSize;
    }

    public long getMaxCount() {
        return maxCount;
    }

    public long getUsedSize() {
        return usedSize;
    }

    public long getUsedCount() {
        return usedCount;
    }

    /**
     * 还剩条数吗
     */
    public boolean hasCountLeft() {
        return usedCount < maxCount;
    }

    /**
     * 还剩容量吗
     */
    public boolean hasSizeLeft() {
        return usedSize < maxSize;
    }

    /**
     * 校验还能不能放下一张 size 字节的图片
     */
    public void ensureCanHold(long size) {
        if (!hasCountLeft()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
        }
        if (!hasSizeLeft()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间不足");
        }
    }

    /**
     * 用量百分比（0~100，保留两位）
     */
    public double sizeUsageRatio() {
        return maxSize <= 0 ? 0D : Math.round(usedSize * 10000.0 / maxSize) / 100.0;
    }

    /**
     * 条数百分比（0~100，保留两位）
     */
    public double countUsageRatio() {
        return maxCount <= 0 ? 0D : Math.round(usedCount * 10000.0 / maxCount) / 100.0;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
