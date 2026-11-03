package com.zhiqin.zhiqinpicturebackend.space.interfaces;

import cn.hutool.core.util.ObjectUtil;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.annotation.SaSpaceCheckPermission;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUserPermissionConstant;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.BaseResponse;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.DeleteRequest;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.ResultUtils;
import com.zhiqin.zhiqinpicturebackend.space.application.SpaceAppService;
import com.zhiqin.zhiqinpicturebackend.space.application.SpaceMemberAppService;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUser;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.assembler.SpaceAssembler;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceUserAddRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceUserEditRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceUserQueryRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.vo.SpaceUserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 空间成员接口层。
 */
@RestController
@RequestMapping("/spaceUser")
public class SpaceUserController {

    @Resource
    private SpaceMemberAppService spaceMemberAppService;

    @Resource
    private SpaceAppService spaceAppService;

    @Resource
    private UserAppService userAppService;

    /**
     * 添加成员到空间
     */
    @PostMapping("/add")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        long id = spaceMemberAppService.add(spaceUserAddRequest.getSpaceId(),
                spaceUserAddRequest.getUserId(), spaceUserAddRequest.getSpaceRole());
        return ResultUtils.success(id);
    }

    /**
     * 从空间移除成员
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        SpaceUser oldSpaceUser = spaceMemberAppService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = spaceMemberAppService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询某个成员在某个空间的信息
     */
    @PostMapping("/get")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = spaceMemberAppService.findBySpaceAndUser(spaceId, userId);
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(spaceUser);
    }

    /**
     * 查询成员信息列表
     */
    @PostMapping("/list")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        List<SpaceUser> spaceUserList = spaceMemberAppService.list(spaceUserQueryRequest.getId(),
                spaceUserQueryRequest.getSpaceId(), spaceUserQueryRequest.getUserId(),
                spaceUserQueryRequest.getSpaceRole());
        return ResultUtils.success(toVOList(spaceUserList));
    }

    /**
     * 编辑成员信息（设置权限）
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest) {
        if (spaceUserEditRequest == null || spaceUserEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserEditRequest, spaceUser);
        spaceMemberAppService.validSpaceUser(spaceUser, false);
        SpaceUser oldSpaceUser = spaceMemberAppService.getById(spaceUserEditRequest.getId());
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = spaceMemberAppService.update(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询我加入的团队空间列表
     */
    @PostMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
        User loginUser = userAppService.getLoginUser(request);
        List<SpaceUser> spaceUserList = spaceMemberAppService.list(null, null, loginUser.getId(), null);
        return ResultUtils.success(toVOList(spaceUserList));
    }

    // ==================== 组装 ====================

    private List<SpaceUserVO> toVOList(List<SpaceUser> spaceUserList) {
        if (spaceUserList == null || spaceUserList.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIdSet = spaceUserList.stream().map(SpaceUser::getUserId).collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream().map(SpaceUser::getSpaceId).collect(Collectors.toSet());
        Map<Long, List<User>> userMap = userAppService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        Map<Long, List<Space>> spaceMap = spaceAppService.listByIds(spaceIdSet).stream()
                .collect(Collectors.groupingBy(Space::getId));
        return spaceUserList.stream().map(spaceUser -> {
            SpaceUserVO vo = SpaceAssembler.toMemberVO(spaceUser);
            List<User> users = userMap.get(spaceUser.getUserId());
            vo.setUser(userAppService.getUserVo(users == null || users.isEmpty() ? null : users.get(0)));
            List<Space> spaces = spaceMap.get(spaceUser.getSpaceId());
            vo.setSpace(SpaceAssembler.toVO(spaces == null || spaces.isEmpty() ? null : spaces.get(0)));
            return vo;
        }).collect(Collectors.toList());
    }
}
