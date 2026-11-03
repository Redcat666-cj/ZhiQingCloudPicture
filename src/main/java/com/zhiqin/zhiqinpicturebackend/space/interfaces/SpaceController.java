package com.zhiqin.zhiqinpicturebackend.space.interfaces;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserConstant;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.annotation.AuthCheck;
import com.zhiqin.zhiqinpicturebackend.space.application.SpacePermissionAppService;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.BaseResponse;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.DeleteRequest;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.ResultUtils;
import com.zhiqin.zhiqinpicturebackend.space.application.SpaceAppService;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.assembler.SpaceAssembler;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceAddRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceEditRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceQueryRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceUpdateRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.vo.SpaceVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 空间接口层。
 */
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private SpaceAppService spaceAppService;

    @Resource
    private UserAppService userAppService;

    /**
     * 历史遗留：前端要展示「当前用户在该空间的权限按钮」，先由权限管理器计算
     * （阶段 2c 会把它挪进 space 上下文的权限服务）
     */
    @Resource
    private SpacePermissionAppService SpacePermissionAppService;

    /**
     * 创建空间
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userAppService.getLoginUser(request);
        long id = spaceAppService.addSpace(SpaceAssembler.toAddCommand(spaceAddRequest), loginUser);
        return ResultUtils.success(id);
    }

    /**
     * 删除空间
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userAppService.getLoginUser(request);
        Space oldSpace = spaceAppService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldSpace.isOwner(loginUser.getId()) && !userAppService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = spaceAppService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新空间（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Space space = SpaceAssembler.toSpace(spaceUpdateRequest);
        space.validate(false);
        Space oldSpace = spaceAppService.getById(spaceUpdateRequest.getId());
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = spaceAppService.update(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取空间（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceAppService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(space);
    }

    /**
     * 根据 id 获取空间（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceAppService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(toVO(space, request));
    }

    /**
     * 分页获取空间列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        PageResult<Space> pageResult = spaceAppService.page(SpaceAssembler.toQuery(spaceQueryRequest));
        Page<Space> page = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        page.setRecords(pageResult.getRecords());
        return ResultUtils.success(page);
    }

    /**
     * 分页获取空间列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest.getPageSize() > 20, ErrorCode.PARAMS_ERROR);
        PageResult<Space> pageResult = spaceAppService.page(SpaceAssembler.toQuery(spaceQueryRequest));
        List<SpaceVO> voList = toVOList(pageResult.getRecords());
        Page<SpaceVO> pageVo = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        pageVo.setRecords(voList);
        return ResultUtils.success(pageVo);
    }

    /**
     * 编辑空间（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        if (spaceEditRequest == null || spaceEditRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Space space = SpaceAssembler.toSpace(spaceEditRequest);
        space.setEditTime(new Date());
        space.validate(false);
        User loginUser = userAppService.getLoginUser(request);
        Space oldSpace = spaceAppService.getById(spaceEditRequest.getId());
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldSpace.isOwner(loginUser.getId()) && !userAppService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = spaceAppService.update(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // ==================== 组装 ====================

    private SpaceVO toVO(Space space, HttpServletRequest request) {
        SpaceVO vo = SpaceAssembler.toVO(space);
        if (vo == null) {
            return null;
        }
        User owner = space.getUserId() == null ? null : userAppService.getById(space.getUserId());
        vo.setUser(userAppService.getUserVo(owner));
        User loginUser = null;
        if (request != null) {
            try {
                loginUser = userAppService.getLoginUser(request);
            } catch (Exception ignored) {
                // 未登录时权限列表为空即可
            }
        }
        vo.setPermissionList(SpacePermissionAppService.getPermissionList(space, loginUser));
        return vo;
    }

    private List<SpaceVO> toVOList(List<Space> spaceList) {
        if (spaceList == null || spaceList.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userMap = userAppService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        return spaceList.stream().map(space -> {
            SpaceVO vo = SpaceAssembler.toVO(space);
            List<User> users = userMap.get(space.getUserId());
            vo.setUser(userAppService.getUserVo(users == null || users.isEmpty() ? null : users.get(0)));
            return vo;
        }).collect(Collectors.toList());
    }
}
