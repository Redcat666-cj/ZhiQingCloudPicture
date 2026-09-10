package com.zhiqin.zhiqinpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhiqin.zhiqinpicturebackend.common.ResultUtils;
import com.zhiqin.zhiqinpicturebackend.constant.UserConstant;
import com.zhiqin.zhiqinpicturebackend.domain.dto.UserQueryRequest;
import com.zhiqin.zhiqinpicturebackend.domain.dto.UserRegisterRequest;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.domain.enums.UserRoleEnum;
import com.zhiqin.zhiqinpicturebackend.domain.vo.LoginUserVo;
import com.zhiqin.zhiqinpicturebackend.domain.vo.UserVO;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.mapper.UserMapper;

import com.zhiqin.zhiqinpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author 崔健
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2026-08-30 20:51:28
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService
    {
    @Override
    public long userRegister(UserRegisterRequest  userRegisterRequest) {
        //检验参数
        if(ObjectUtil.isEmpty(userRegisterRequest)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }

        if(userRegisterRequest.getUserName().length()<4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号过短");
        }

        if((userRegisterRequest.getPassword().length()<8||userRegisterRequest.getPassword().length()>32)){
            throw  new BusinessException(ErrorCode.PARAMS_ERROR,"密码不少于8位但不超过32");

        }
        if(!userRegisterRequest.getPassword().equals(userRegisterRequest.getConfirmPassword())){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次密码不一致");
        }
        //检查数据是否重复
        QueryWrapper<User>   queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount",userRegisterRequest.getUserName());
        Long count = this.baseMapper.selectCount(queryWrapper);
        if(count>0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号重复");
        }
        //密码要加密
        String encryptPassword = getEncryptPassword(userRegisterRequest);
        //插入数据库
        User user = new User();
        user.setUserName("无名");
        user.setUserAccount(userRegisterRequest.getUserName());
        user.setUserPassword(encryptPassword);
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean flag = save(user);
        if(!flag){
            throw  new BusinessException(ErrorCode.SYSTEM_ERROR,"注册失败");
        }


        return user.getId(); //id??哪来的mybatis框架帮我们做的 主键回填
    }

    @Override
    public String getEncryptPassword(UserRegisterRequest userRegisterRequest){
        final String key = "redcat";
        return DigestUtils.md5DigestAsHex((key+userRegisterRequest.getPassword()).getBytes());
    }
    @Override
    public String getEncryptPassword(String password){
            final String key = "redcat";
            return DigestUtils.md5DigestAsHex((key+password).getBytes());
    }
    @Override
    public LoginUserVo userLogin(String userAccount , String userPassword, HttpServletRequest request){
        //校验
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        //加密
        String encryptPassword = getEncryptPassword(userPassword);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount",userAccount);
        queryWrapper.eq("userPassword",encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        //不存在跑异常
        if(user == null){
            log.info("User login fail,userAccount:{} cannot be finded",userAccount);
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在或密码错误");
        }
        //保存用户登录状态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE,user);
        return BeanUtil.copyProperties(user,LoginUserVo.class);
    }

        @Override
        public UserVO getUserVo(User user) {
           return  BeanUtil.copyProperties(user,UserVO.class);
        }

        @Override
        public QueryWrapper<User> getQueryWrapper(UserQueryRequest request) {
                if(request == null){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"请求参数为空");
                }
            Long id = request.getId();
            String userAccount = request.getUserAccount();
            String userName = request.getUserName();
            String userProfile = request.getUserProfile();
            String userRole = request.getUserRole();
            String sortField = request.getSortField();
            String sortOrder = request.getSortOrder();
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
            queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
            queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
            queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
            queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
            queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
            return queryWrapper;
        }

        @Override
        public List<UserVO> getUserVos(List<User> userList) {
            if(ObjectUtil.isEmpty(userList)){
                return   Collections.emptyList();
            }
           return userList.stream().map(this::getUserVo).collect(Collectors.toList());

        }

        @Override
        public Boolean userLogout(HttpServletRequest request) {
            Object attribute = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
            if (attribute != null) {
                request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
            }
            else {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            return true;
        }

        @Override
        public User getLoginUser(HttpServletRequest request) {
            //判断是否已登录
            Object attribute = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
            User user = (User) attribute;
            if(user == null||user.getId()==null){
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            //从数据库查(追求性能直接返回)
            Long userId = user.getId();
            user=getById(userId);
            if(user==null){
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            return user;
        }
    }




