package com.zhiqin.zhiqinpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiqin.zhiqinpicturebackend.domain.space.SpaceAddRequest;
import com.zhiqin.zhiqinpicturebackend.domain.space.SpaceQueryRequest;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Space;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;

import com.baomidou.mybatisplus.extension.service.IService;

import com.zhiqin.zhiqinpicturebackend.domain.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author 崔健
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-10-03 12:53:02
*/
public interface SpaceService extends IService<Space> {
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest request);

    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    void validSpace(Space space ,boolean add);

    void fillSpaceBySpecialLevel(Space space);

    void checkSpaceAuth(User loginUser, Space space);
}
