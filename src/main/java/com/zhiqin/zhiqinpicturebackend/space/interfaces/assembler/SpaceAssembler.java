package com.zhiqin.zhiqinpicturebackend.space.interfaces.assembler;

import com.zhiqin.zhiqinpicturebackend.space.application.command.AddSpaceCommand;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceQuery;
import com.zhiqin.zhiqinpicturebackend.space.domain.SpaceUser;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceAddRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceEditRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceQueryRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.dto.SpaceUpdateRequest;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.vo.SpaceUserVO;
import com.zhiqin.zhiqinpicturebackend.space.interfaces.vo.SpaceVO;
import org.springframework.beans.BeanUtils;

/**
 * 空间接口层装配器。
 */
public final class SpaceAssembler {

    private SpaceAssembler() {
    }

    public static SpaceVO toVO(Space space) {
        if (space == null) {
            return null;
        }
        SpaceVO vo = new SpaceVO();
        BeanUtils.copyProperties(space, vo);
        return vo;
    }

    public static SpaceUserVO toMemberVO(SpaceUser spaceUser) {
        if (spaceUser == null) {
            return null;
        }
        SpaceUserVO vo = new SpaceUserVO();
        BeanUtils.copyProperties(spaceUser, vo);
        return vo;
    }

    public static AddSpaceCommand toAddCommand(SpaceAddRequest request) {
        if (request == null) {
            return null;
        }
        AddSpaceCommand command = new AddSpaceCommand();
        command.setSpaceName(request.getSpaceName());
        command.setSpaceLevel(request.getSpaceLevel());
        command.setSpaceType(request.getSpaceType());
        return command;
    }

    public static Space toSpace(SpaceUpdateRequest request) {
        if (request == null) {
            return null;
        }
        Space space = new Space();
        BeanUtils.copyProperties(request, space);
        return space;
    }

    public static Space toSpace(SpaceEditRequest request) {
        if (request == null) {
            return null;
        }
        Space space = new Space();
        BeanUtils.copyProperties(request, space);
        return space;
    }

    public static SpaceQuery toQuery(SpaceQueryRequest request) {
        SpaceQuery query = new SpaceQuery();
        if (request == null) {
            return query;
        }
        query.setId(request.getId());
        query.setUserId(request.getUserId());
        query.setSpaceName(request.getSpaceName());
        query.setSpaceLevel(request.getSpaceLevel());
        query.setSpaceType(request.getSpaceType());
        query.setSortField(request.getSortField());
        if (request.getSortOrder() != null) {
            query.setSortOrder(request.getSortOrder());
        }
        query.setCurrent(request.getCurrent());
        query.setSize(request.getPageSize());
        return query;
    }
}
