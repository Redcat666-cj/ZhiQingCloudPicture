package com.zhiqin.zhiqinpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiqin.zhiqinpicturebackend.domain.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhiqin.zhiqinpicturebackend.domain.spaceuser.SpaceUserAddRequest;
import com.zhiqin.zhiqinpicturebackend.domain.spaceuser.SpaceUserQueryRequest;
import com.zhiqin.zhiqinpicturebackend.domain.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 崔健
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2026-10-23 15:08:17
*/
public interface SpaceUserService extends IService<SpaceUser> {

    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);


    void validSpaceUser(SpaceUser spaceUser, boolean add);
}
