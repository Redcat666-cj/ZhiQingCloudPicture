package com.zhiqin.zhiqinpicturebackend.identity.application;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserConstant;
import com.zhiqin.zhiqinpicturebackend.identity.application.command.AddUserCommand;
import com.zhiqin.zhiqinpicturebackend.identity.application.command.RegisterCommand;
import com.zhiqin.zhiqinpicturebackend.identity.application.command.UpdateUserCommand;
import com.zhiqin.zhiqinpicturebackend.identity.application.dto.LoginUserVo;
import com.zhiqin.zhiqinpicturebackend.identity.application.dto.UserVO;
import com.zhiqin.zhiqinpicturebackend.identity.domain.EncryptedPassword;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserQuery;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserRepository;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserRoleEnum;
import com.zhiqin.zhiqinpicturebackend.space.infrastructure.StpKit;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户应用服务：identity 上下文的用例编排入口。
 *
 * <p>职责边界：只做「校验 → 调用领域对象/仓储 → 组装输出」，不写 SQL、不拼 QueryWrapper、
 * 不做 hash 之类的技术细节（都在领域/基础设施层）。</p>
 *
 * <p>历史包袱说明（阶段 6 收口）：{@link #getLoginUser} 直接吃 {@code HttpServletRequest}
 * 并读写 session，属于接口层职责；因为图片/空间等上下文当前有 29 处调用，先保留签名，
 * 等各上下文改造完成后统一换成 interfaces 层的登录态解析器。</p>
 */
@Slf4j
@Service
public class UserAppService {

    /**
     * 注册默认昵称
     */
    private static final String DEFAULT_NICKNAME = "无名";

    /**
     * 管理员新增用户时的默认密码
     */
    private static final String DEFAULT_PASSWORD = "12345678";

    /**
     * 密码长度限制
     */
    private static final int PASSWORD_MIN_LENGTH = 8;

    private static final int PASSWORD_MAX_LENGTH = 32;

    /**
     * 账号最小长度
     */
    private static final int ACCOUNT_MIN_LENGTH = 4;

    @Resource
    private UserRepository userRepository;

    // ==================== 用例 ====================

    /**
     * 注册新用户
     *
     * @return 新用户 id
     */
    public Long register(RegisterCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (command.getAccount() == null || command.getAccount().length() < ACCOUNT_MIN_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        String password = command.getPassword();
        if (password == null || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不少于8位但不超过32");
        }
        if (!password.equals(command.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
        }
        if (userRepository.existsByAccount(command.getAccount())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        User user = new User();
        user.setUserName(DEFAULT_NICKNAME);
        user.setUserAccount(command.getAccount());
        user.setUserPassword(EncryptedPassword.ofRaw(password).value());
        user.setUserRole(UserRoleEnum.USER.getValue());
        User saved = userRepository.save(user);
        if (saved == null || saved.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败");
        }
        return saved.getId();
    }

    /**
     * 登录：校验账号密码 + 建立登录态
     */
    public LoginUserVo login(String userAccount, String userPassword, HttpServletRequest request) {
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < ACCOUNT_MIN_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < PASSWORD_MIN_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        User user = userRepository.findByAccount(userAccount);
        if (user == null || !EncryptedPassword.ofEncrypted(user.getUserPassword())
                .matches(EncryptedPassword.ofRaw(userPassword))) {
            log.info("User login fail, userAccount:{} cannot be found", userAccount);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 保存用户登录状态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        // 同步建立「空间账号体系」(StpKit.SPACE) 的登录态：
        // @SaSpaceCheckPermission 校验的是 space 这个 StpLogic，不登录会一直返回 40100「未能读取到有效 token」
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSessionByLoginId(user.getId()).set(UserConstant.USER_LOGIN_STATE, user);
        return BeanUtil.copyProperties(user, LoginUserVo.class);
    }

    /**
     * 获取当前登录用户（未登录抛 NOT_LOGIN_ERROR）
     */
    public User getLoginUser(HttpServletRequest request) {
        Object attribute = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User user = (User) attribute;
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User current = userRepository.findById(user.getId());
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return current;
    }

    /**
     * 退出登录
     */
    public Boolean logout(HttpServletRequest request) {
        Object attribute = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (attribute == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    /**
     * 管理员新增用户（使用默认密码）
     */
    public Long addUser(AddUserCommand command) {
        User user = new User();
        BeanUtil.copyProperties(command, user);
        user.setUserPassword(EncryptedPassword.ofRaw(DEFAULT_PASSWORD).value());
        User saved = userRepository.save(user);
        if (saved == null || saved.getId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return saved.getId();
    }

    /**
     * 管理员更新用户（只更新非空字段）
     */
    public boolean updateUser(UpdateUserCommand command) {
        User user = new User();
        BeanUtil.copyProperties(command, user);
        boolean updated = userRepository.update(user);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return true;
    }

    /**
     * 用户自助更新资料：只允许改自己的昵称和头像
     */
    public boolean updateMyUser(Long loginUserId, UpdateUserCommand command) {
        User user = new User();
        user.setId(loginUserId);
        user.setUserName(command.getUserName());
        user.setUserAvatar(command.getUserAvatar());
        boolean updated = userRepository.update(user);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return true;
    }

    /**
     * 删除用户（逻辑删除）
     */
    public boolean deleteUser(Long id) {
        return userRepository.removeById(id);
    }

    // ==================== 查询 ====================

    public User getById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> listByIds(Collection<Long> ids) {
        return userRepository.findByIds(ids);
    }

    public PageResult<User> page(UserQuery query) {
        return userRepository.page(query);
    }

    public boolean isAdmin(User user) {
        return user != null && user.isAdmin();
    }

    public UserVO getUserVo(User user) {
        if (user == null) {
            return null;
        }
        return BeanUtil.copyProperties(user, UserVO.class);
    }

    public List<UserVO> getUserVos(List<User> userList) {
        if (userList == null || userList.isEmpty()) {
            return Collections.emptyList();
        }
        return userList.stream().map(this::getUserVo).collect(Collectors.toList());
    }
}
