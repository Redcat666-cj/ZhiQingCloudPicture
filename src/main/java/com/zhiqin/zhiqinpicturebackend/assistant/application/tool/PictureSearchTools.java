package com.zhiqin.zhiqinpicturebackend.assistant.application.tool;

import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.assistant.domain.PictureBrief;
import com.zhiqin.zhiqinpicturebackend.assistant.domain.SpaceBrief;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.picture.application.PictureAppService;
import com.zhiqin.zhiqinpicturebackend.picture.domain.Picture;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureQuery;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureRepository;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureReviewStatusEnum;
import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;
import com.zhiqin.zhiqinpicturebackend.space.application.SpaceAppService;
import com.zhiqin.zhiqinpicturebackend.space.application.SpaceMemberAppService;
import com.zhiqin.zhiqinpicturebackend.space.application.SpacePermissionAppService;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceQuery;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceRepository;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUser;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUserPermissionConstant;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceMemberRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * AI 助手的图片检索工具集（Spring AI Tool Calling）。
 *
 * <p>安全红线：工具内部身份一律取自 ToolContext 里的 userId（由应用层从登录态写入），
 * 绝不信任模型生成的任何身份参数；空间访问必须持有 picture:view 权限。
 * 权限判断复用 {@link SpacePermissionAppService#getPermissionList}（不依赖 HTTP 请求上下文），
 * 分表路由由 {@link PictureRepository#page} 内部的 ThreadLocal 机制完成。</p>
 */
@Component
public class PictureSearchTools {

    private static final String CTX_USER_ID = "userId";

    /**
     * 工具查到图片后经此回调把结果实时推给前端（SSE pictures 事件）
     */
    private static final String CTX_PICTURE_SINK = "pictureSink";

    @Resource
    private PictureRepository pictureRepository;

    @Resource
    private PictureAppService PictureAppService;

    @Resource
    private SpaceRepository spaceRepository;

    @Resource
    private SpaceMemberRepository spaceMemberRepository;

    @Resource
    private SpaceAppService SpaceAppService;

    @Resource
    private SpaceMemberAppService SpaceMemberAppService;

    @Resource
    private SpacePermissionAppService SpacePermissionAppService;

    @Resource
    private UserAppService UserAppService;

    @Tool(description = """
            按条件搜索照片。不传 spaceId 时在公共图库搜索（仅已审核通过的照片）；
            传 spaceId 时在指定空间内搜索。返回照片摘要列表。""")
    public List<PictureBrief> searchPictures(
            @ToolParam(description = "标题或简介关键词，没有关键词时传空字符串", required = false) String searchText,
            @ToolParam(description = "标签列表，如 [\"风景\"]；没有标签时传空", required = false) List<String> tags,
            @ToolParam(description = "空间 id，数字字符串；不传表示公共图库", required = false) Long spaceId,
            ToolContext toolContext) {
        Long userId = userId(toolContext);
        if (userId == null) {
            return new ArrayList<>();
        }
        User loginUser = UserAppService.getById(userId);
        PictureQuery query = new PictureQuery();
        query.setSearchText(blankToNull(searchText));
        if (tags != null && !tags.isEmpty()) {
            query.setTags(tags);
        }
        query.setSize(10);
        query.setCurrent(1);
        query.setSortField("createTime");
        query.setSortOrder("descend");
        if (spaceId == null) {
            // 公共图库：与公开浏览接口一致，只展示审核通过的图片
            query.setNullSpaceId(true);
            query.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else {
            Space space = SpaceAppService.getById(spaceId);
            if (space == null || !hasViewPermission(space, loginUser)) {
                return new ArrayList<>();
            }
            query.setSpaceId(spaceId);
        }
        PageResult<Picture> page = pictureRepository.page(query);
        List<PictureBrief> briefs = toBriefs(page == null ? null : page.getRecords());
        publish(toolContext, briefs);
        return briefs;
    }

    @Tool(description = "查看某张照片的详细信息（名称、简介、标签、分类、上传时间）。")
    public PictureBrief getPictureDetail(
            @ToolParam(description = "照片 id，数字字符串") long pictureId,
            ToolContext toolContext) {
        Long userId = userId(toolContext);
        if (userId == null) {
            return new PictureBrief();
        }
        Picture picture = PictureAppService.getById(pictureId);
        if (picture == null) {
            return new PictureBrief();
        }
        User loginUser = UserAppService.getById(userId);
        if (picture.getSpaceId() == null) {
            // 公共图库：未过审的照片仅本人/管理员可见
            boolean pending = picture.getReviewStatus() != null
                    && picture.getReviewStatus() != PictureReviewStatusEnum.PASS.getValue();
            if (pending) {
                boolean ownOrAdmin = picture.getUserId() != null && picture.getUserId().equals(userId)
                        || (loginUser != null && UserAppService.isAdmin(loginUser));
                if (!ownOrAdmin) {
                    return new PictureBrief();
                }
            }
        } else {
            // 空间图库：必须持有 picture:view 权限（私有空间=本人/管理员，团队空间=按成员角色）
            Space space = SpaceAppService.getById(picture.getSpaceId());
            if (!hasViewPermission(space, loginUser)) {
                return new PictureBrief();
            }
        }
        PictureBrief brief = PictureBrief.from(picture);
        publish(toolContext, List.of(brief));
        return brief;
    }

    @Tool(description = """
            列出当前用户可以访问的全部空间（个人空间和加入的团队空间），
            返回空间 id、名称、类型和级别。用户说"在我的空间找"时，先调用此工具确定 spaceId。""")
    public List<SpaceBrief> listAccessibleSpaces(ToolContext toolContext) {
        Long userId = userId(toolContext);
        if (userId == null) {
            return new ArrayList<>();
        }
        List<Space> spaces = new ArrayList<>();
        // 自己创建的空间
        SpaceQuery ownedQuery = new SpaceQuery();
        ownedQuery.setUserId(userId);
        ownedQuery.setSize(50);
        PageResult<Space> owned = spaceRepository.page(ownedQuery);
        if (owned != null && owned.getRecords() != null) {
            spaces.addAll(owned.getRecords());
        }
        // 加入的团队空间
        List<SpaceUser> memberships = spaceMemberRepository.list(null, null, userId, null);
        if (memberships != null && !memberships.isEmpty()) {
            Set<Long> joinedIds = new LinkedHashSet<>();
            memberships.forEach(member -> {
                if (member.getSpaceId() != null) {
                    joinedIds.add(member.getSpaceId());
                }
            });
            if (!joinedIds.isEmpty()) {
                List<Space> joined = spaceRepository.findByIds(joinedIds);
                if (joined != null) {
                    spaces.addAll(joined);
                }
            }
        }
        return SpaceBrief.fromList(spaces);
    }

    @Tool(description = "列出某个空间里已使用的标签，帮助在用户描述模糊时确定搜索用词。")
    public List<String> listSpaceTags(
            @ToolParam(description = "空间 id，数字字符串") long spaceId,
            ToolContext toolContext) {
        Long userId = userId(toolContext);
        if (userId == null) {
            return new ArrayList<>();
        }
        Space space = SpaceAppService.getById(spaceId);
        User loginUser = UserAppService.getById(userId);
        if (space == null || !hasViewPermission(space, loginUser)) {
            return new ArrayList<>();
        }
        List<Picture> pictures = pictureRepository.listBySpace(spaceId);
        Set<String> tagSet = new LinkedHashSet<>();
        if (pictures != null) {
            for (Picture picture : pictures) {
                if (picture.getTags() == null || picture.getTags().isBlank()) {
                    continue;
                }
                try {
                    List<String> tags = JSONUtil.toList(picture.getTags(), String.class);
                    if (tags != null) {
                        tagSet.addAll(tags);
                    }
                } catch (Exception ignored) {
                    // 单条 tags 解析失败不影响整体
                }
            }
        }
        return tagSet.stream().limit(30).toList();
    }

    // ==================== 内部方法 ====================

    private Long userId(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object userId = toolContext.getContext().get(CTX_USER_ID);
        return userId instanceof Long id ? id : null;
    }

    @SuppressWarnings("unchecked")
    private void publish(ToolContext toolContext, List<PictureBrief> briefs) {
        if (briefs == null || briefs.isEmpty() || toolContext == null) {
            return;
        }
        Object sink = toolContext.getContext().get(CTX_PICTURE_SINK);
        if (sink instanceof Consumer<?> consumer) {
            ((Consumer<List<PictureBrief>>) consumer).accept(briefs);
        }
    }

    /**
     * 空间查看权限：公共图库语义（space=null）由调用方另行处理；
     * 私有空间=本人/管理员，团队空间=按成员角色判定。
     */
    private boolean hasViewPermission(Space space, User loginUser) {
        if (space == null || loginUser == null) {
            return false;
        }
        List<String> permissions = SpacePermissionAppService.getPermissionList(space, loginUser);
        return permissions.contains(SpaceUserPermissionConstant.PICTURE_VIEW);
    }

    private String blankToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private List<PictureBrief> toBriefs(List<Picture> pictures) {
        List<PictureBrief> briefs = new ArrayList<>();
        if (pictures == null) {
            return briefs;
        }
        for (Picture picture : pictures) {
            briefs.add(PictureBrief.from(picture));
        }
        return briefs;
    }
}
