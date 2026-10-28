package com.zhiqin.zhiqinpicturebackend.api.aliyunai;

/**
 * 扩图输出尺寸计算。
 *
 * <p>万相图像编辑（i2i）对输出尺寸有硬性限制：</p>
 * <ul>
 *   <li>总像素上限 2048 × 2048 = 4194304（2K）</li>
 *   <li>总像素下限 768 × 768 = 589824</li>
 *   <li>宽高比不超过 8:1</li>
 * </ul>
 *
 * <p>关键点：把「原图 × 扩展倍数」缩放到上限内之后，<b>不能直接四舍五入</b>——
 * 两个方向都向上进位会把乘积重新顶到上限之上，
 * 于是接口会报 {@code Total pixels (4195510) exceeds the limit for i2i scenario}。
 * 所以这里在取整之后再做一次逐像素收敛，保证结果严格落在区间内。</p>
 */
public final class OutPaintingSizeCalculator {

    /**
     * 总像素上限（2K）
     */
    public static final long MAX_PIXELS = 2048L * 2048L;

    /**
     * 总像素下限
     */
    public static final long MIN_PIXELS = 768L * 768L;

    /**
     * 最大宽高比
     */
    public static final double MAX_ASPECT = 8D;

    private static final int DEFAULT_SIDE = 1024;

    private OutPaintingSizeCalculator() {
    }

    /**
     * 计算扩图请求参数里的 size，格式 {@code 宽*高}
     *
     * @param width  原图宽，为空时按 1024 处理
     * @param height 原图高，为空时按 1024 处理
     * @param xScale 水平扩展倍数
     * @param yScale 垂直扩展倍数
     */
    public static String buildSize(Integer width, Integer height, float xScale, float yScale) {
        double targetWidth = (width == null || width < 1 ? DEFAULT_SIDE : width) * (xScale <= 0 ? 1D : xScale);
        double targetHeight = (height == null || height < 1 ? DEFAULT_SIDE : height) * (yScale <= 0 ? 1D : yScale);

        // 1. 收敛宽高比
        if (targetWidth / targetHeight > MAX_ASPECT) {
            targetWidth = targetHeight * MAX_ASPECT;
        } else if (targetHeight / targetWidth > MAX_ASPECT) {
            targetHeight = targetWidth * MAX_ASPECT;
        }

        // 2. 按总像素等比缩放到 [MIN_PIXELS, MAX_PIXELS]
        double pixels = targetWidth * targetHeight;
        if (pixels > MAX_PIXELS) {
            double scale = Math.sqrt(MAX_PIXELS / pixels);
            targetWidth *= scale;
            targetHeight *= scale;
        } else if (pixels < MIN_PIXELS) {
            double scale = Math.sqrt(MIN_PIXELS / pixels);
            targetWidth *= scale;
            targetHeight *= scale;
        }

        // 3. 取整后再逐像素收敛：四舍五入会把乘积顶回区间之外
        long outWidth = Math.max(1L, Math.round(targetWidth));
        long outHeight = Math.max(1L, Math.round(targetHeight));
        while (outWidth * outHeight > MAX_PIXELS) {
            if (outWidth >= outHeight && outWidth > 1) {
                outWidth--;
            } else if (outHeight > 1) {
                outHeight--;
            } else {
                break;
            }
        }
        while (outWidth * outHeight < MIN_PIXELS) {
            if (outWidth <= outHeight) {
                outWidth++;
            } else {
                outHeight++;
            }
        }
        return outWidth + "*" + outHeight;
    }
}
