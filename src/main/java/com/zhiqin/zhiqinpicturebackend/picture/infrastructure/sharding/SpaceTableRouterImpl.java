package com.zhiqin.zhiqinpicturebackend.picture.infrastructure.sharding;

import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureTableRouter;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 分表路由适配器：把领域端口 {@link PictureTableRouter} 落到具体的 ThreadLocal + 建表 + 反查实现上。
 *
 * <p>ThreadLocal（{@link SpaceTableContext}）只允许出现在这一层，领域层完全看不到它。</p>
 */
@Component
public class SpaceTableRouterImpl implements PictureTableRouter {

    @Resource
    private PictureTableLocator pictureTableLocator;

    @Resource
    private SpaceTableManager spaceTableManager;

    @Override
    public Long locateSpaceId(Long pictureId) {
        return pictureTableLocator.locateSpaceId(pictureId);
    }

    @Override
    public <T> T inSpace(Long spaceId, Supplier<T> action) {
        Long previous = SpaceTableContext.get();
        SpaceTableContext.set(spaceId);
        try {
            return action.get();
        } finally {
            SpaceTableContext.set(previous);
        }
    }

    @Override
    public Long currentSpaceId() {
        return SpaceTableContext.get();
    }

    @Override
    public void ensureSpaceTable(Long spaceId) {
        spaceTableManager.ensureSpaceTable(spaceId);
    }

    @Override
    public List<Long> listSpaceIds() {
        return spaceTableManager.listSpaceIds();
    }

    @Override
    public List<Long> allScopes() {
        List<Long> scopes = new ArrayList<>();
        // null 代表公共图库 picture_public
        scopes.add(null);
        scopes.addAll(spaceTableManager.listSpaceIds());
        return scopes;
    }
}
