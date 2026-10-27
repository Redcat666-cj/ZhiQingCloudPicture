package com.zhiqin.zhiqinpicturebackend.manager.table;

import java.util.function.Supplier;

/**
 * 动态分表上下文：当前线程正在操作「哪个空间的图片表」。
 *
 * <p>spaceId 为 null 表示公共图库，对应物理表 picture_public；
 * spaceId 不为 null 表示私有空间，对应物理表 picture_{spaceId}。</p>
 *
 * <p>因为表名是运行时决定的，ThreadLocal 是最自然的载体：
 * 谁知道自己要查哪个空间，谁就先用 {@link #run} / {@link #call} 把上下文包起来，
 * MyBatis-Plus 的动态表名拦截器再去读它（见 PictureTableNameHandler）。</p>
 *
 * <p>注意：@Async / 线程池里 ThreadLocal 不会传递，异步任务里要手动 set。</p>
 */
public final class SpaceTableContext {

    private static final ThreadLocal<Long> SPACE_ID_HOLDER = new ThreadLocal<>();

    private SpaceTableContext() {
    }

    public static void set(Long spaceId) {
        if (spaceId == null) {
            SPACE_ID_HOLDER.remove();
        } else {
            SPACE_ID_HOLDER.set(spaceId);
        }
    }

    public static Long get() {
        return SPACE_ID_HOLDER.get();
    }

    public static void clear() {
        SPACE_ID_HOLDER.remove();
    }

    /**
     * 在指定空间的表上执行一段逻辑，并恢复原来的上下文（支持嵌套调用）。
     *
     * @param spaceId 空间 id，null 表示公共图库
     * @param action  要执行的逻辑
     */
    public static <T> T call(Long spaceId, Supplier<T> action) {
        Long previous = get();
        set(spaceId);
        try {
            return action.get();
        } finally {
            set(previous);
        }
    }

    /**
     * 在指定空间的表上执行一段无返回值逻辑，并恢复原来的上下文。
     */
    public static void run(Long spaceId, Runnable action) {
        Long previous = get();
        set(spaceId);
        try {
            action.run();
        } finally {
            set(previous);
        }
    }
}
