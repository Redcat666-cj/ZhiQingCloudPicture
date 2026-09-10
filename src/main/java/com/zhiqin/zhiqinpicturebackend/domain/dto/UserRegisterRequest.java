package com.zhiqin.zhiqinpicturebackend.domain.dto;

import lombok.Data;

import java.io.Serializable;

/*
* 用户注册请求
* */
@Data
public class UserRegisterRequest implements Serializable { //Serializable 序列化

    private static final long serialVersionUID = 23324321L;

    private String  userName;

    private String  password;

    private String  confirmPassword;



}
