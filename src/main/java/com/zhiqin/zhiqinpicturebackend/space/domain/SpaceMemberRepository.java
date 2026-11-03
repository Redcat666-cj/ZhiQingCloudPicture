package com.zhiqin.zhiqinpicturebackend.space.domain;

import java.util.List;

/**
 * 空间成员仓储（领域端口）。
 */
public interface SpaceMemberRepository {

    SpaceUser findById(Long id);

    /**
     * 查某人在某空间的成员记录（团队空间权限判断的核心入口）
     */
    SpaceUser findBySpaceAndUser(Long spaceId, Long userId);

    /**
     * 按条件查询成员列表（id/spaceId/userId/spaceRole 均可为空）
     */
    List<SpaceUser> list(Long id, Long spaceId, Long userId, String spaceRole);

    SpaceUser save(SpaceUser spaceUser);

    boolean update(SpaceUser spaceUser);

    boolean removeById(Long id);
}
