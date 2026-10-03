package com.zhiqin.zhiqinpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiqin.zhiqinpicturebackend.domain.dto.UserQueryRequest;
import com.zhiqin.zhiqinpicturebackend.domain.dto.UserRegisterRequest;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhiqin.zhiqinpicturebackend.domain.vo.LoginUserVo;
import com.zhiqin.zhiqinpicturebackend.domain.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 崔健
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2026-08-30 20:51:28
*/
public interface UserService extends IService<User> {

    long userRegister(UserRegisterRequest userRegisterRequest);


    String getEncryptPassword(UserRegisterRequest userRegisterRequest);

    String getEncryptPassword(String password);

    LoginUserVo userLogin(String username, String password, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /*
    * 用户注销
    * */
    Boolean userLogout(HttpServletRequest request);


    /*
    * getUserVo*/
    UserVO getUserVo(User user) ;


    List<UserVO> getUserVos(List<User> userList);


    QueryWrapper<User> getQueryWrapper(UserQueryRequest request);

    boolean isAdmin(User user);
}
