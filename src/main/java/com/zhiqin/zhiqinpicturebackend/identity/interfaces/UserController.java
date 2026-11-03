package com.zhiqin.zhiqinpicturebackend.identity.interfaces;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserConstant;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import com.zhiqin.zhiqinpicturebackend.identity.application.dto.LoginUserVo;
import com.zhiqin.zhiqinpicturebackend.identity.application.dto.UserVO;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.annotation.AuthCheck;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.assembler.UserAssembler;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.dto.UserAddRequest;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.dto.UserLoginRequest;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.dto.UserQueryRequest;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.dto.UserRegisterRequest;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.dto.UserUpdateRequest;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.BaseResponse;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.DeleteRequest;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户接口层（identity 上下文）。
 *
 * <p>只做三件事：参数接收与校验、DTO ↔ 命令/领域对象的装配、调用应用服务并包装返回值。
 * 不再直接使用 Mapper / ServiceImpl / QueryWrapper。</p>
 *
 * <p>接口路径、请求体、响应字段与重构前完全一致（包括 /user/get 仍返回 User 实体，
 * 以保持前端契约；后续阶段再评估是否收敛到 VO）。</p>
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserAppService userAppService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        long userId = userAppService.register(UserAssembler.toRegisterCommand(userRegisterRequest));
        return ResultUtils.success(userId);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVo> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        LoginUserVo loginUserVo = userAppService.login(
                userLoginRequest.getUserAccount(), userLoginRequest.getUserPassword(), request);
        return ResultUtils.success(loginUserVo);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVo> getUserLogin(HttpServletRequest request) {
        return ResultUtils.success(BeanUtil.copyProperties(userAppService.getLoginUser(request), LoginUserVo.class));
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userAppService.logout(request));
    }

    /**
     * 创建用户（仅管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        Long userId = userAppService.addUser(UserAssembler.toAddUserCommand(userAddRequest));
        return ResultUtils.success(userId);
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userAppService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取用户包装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userAppService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(userAppService.getUserVo(user));
    }

    /**
     * 删除用户（仅管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(userAppService.deleteUser(deleteRequest.getId()));
    }

    /**
     * 更新用户（仅管理员）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(userAppService.updateUser(UserAssembler.toUpdateUserCommand(userUpdateRequest)));
    }

    /**
     * 用户自助更新个人信息（仅允许修改自己的名称和头像）。
     *
     * <p>不复用 /update：那是管理员接口可改任意用户，直接放开会越权。
     * 这里强制取登录态的 id，且只写名称、头像两个字段。</p>
     */
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userAppService.getLoginUser(request);
        return ResultUtils.success(userAppService.updateMyUser(loginUser.getId(),
                UserAssembler.toUpdateUserCommand(userUpdateRequest)));
    }

    /**
     * 分页获取用户列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<UserVO>> listUserPageVo(@RequestBody UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        PageResult<User> pageResult = userAppService.page(UserAssembler.toQuery(userQueryRequest));
        List<UserVO> userVos = userAppService.getUserVos(pageResult.getRecords());
        Page<UserVO> pageVo = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        pageVo.setRecords(userVos);
        return ResultUtils.success(pageVo);
    }
}
