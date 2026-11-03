package com.zhiqin.zhiqinpicturebackend.identity.interfaces.assembler;

import com.zhiqin.zhiqinpicturebackend.identity.application.command.AddUserCommand;
import com.zhiqin.zhiqinpicturebackend.identity.application.command.RegisterCommand;
import com.zhiqin.zhiqinpicturebackend.identity.application.command.UpdateUserCommand;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserQuery;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.dto.UserAddRequest;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.dto.UserQueryRequest;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.dto.UserRegisterRequest;
import com.zhiqin.zhiqinpicturebackend.identity.interfaces.dto.UserUpdateRequest;

/**
 * 用户接口层装配器：请求 DTO → 应用层命令 / 领域对象，领域对象 → 响应 VO。
 *
 * <p>它属于接口层，所以允许依赖应用层与领域层，反向不行。</p>
 */
public final class UserAssembler {

    private UserAssembler() {
    }

    public static RegisterCommand toRegisterCommand(UserRegisterRequest request) {
        if (request == null) {
            return null;
        }
        // 历史契约：注册请求里的 userName 字段承载的是「账号」
        return new RegisterCommand(request.getUserName(), request.getPassword(), request.getConfirmPassword());
    }

    public static AddUserCommand toAddUserCommand(UserAddRequest request) {
        if (request == null) {
            return null;
        }
        AddUserCommand command = new AddUserCommand();
        command.setUserAccount(request.getUserAccount());
        command.setUserName(request.getUserName());
        command.setUserAvatar(request.getUserAvatar());
        command.setUserProfile(request.getUserProfile());
        command.setUserRole(request.getUserRole());
        return command;
    }

    public static UpdateUserCommand toUpdateUserCommand(UserUpdateRequest request) {
        if (request == null) {
            return null;
        }
        UpdateUserCommand command = new UpdateUserCommand();
        command.setId(request.getId());
        command.setUserName(request.getUserName());
        command.setUserAvatar(request.getUserAvatar());
        command.setUserProfile(request.getUserProfile());
        command.setUserRole(request.getUserRole());
        return command;
    }

    public static UserQuery toQuery(UserQueryRequest request) {
        UserQuery query = new UserQuery();
        if (request == null) {
            return query;
        }
        query.setId(request.getId());
        query.setUserAccount(request.getUserAccount());
        query.setUserName(request.getUserName());
        query.setUserProfile(request.getUserProfile());
        query.setUserRole(request.getUserRole());
        query.setSortField(request.getSortField());
        if (request.getSortOrder() != null) {
            query.setSortOrder(request.getSortOrder());
        }
        query.setCurrent(request.getCurrent());
        query.setSize(request.getPageSize());
        return query;
    }

    public static boolean hasSortDsl(UserQueryRequest request) {
        return request != null && request.getSortField() != null && !request.getSortField().isEmpty();
    }

    public static boolean isUserMissing(User user) {
        return user == null;
    }
}
