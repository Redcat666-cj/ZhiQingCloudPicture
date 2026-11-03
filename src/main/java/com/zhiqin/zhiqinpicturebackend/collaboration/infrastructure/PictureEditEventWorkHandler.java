package com.zhiqin.zhiqinpicturebackend.collaboration.infrastructure;

import com.lmax.disruptor.WorkHandler;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.collaboration.interfaces.PictureEditHandler;
import com.zhiqin.zhiqinpicturebackend.collaboration.interfaces.PictureEditMessageJson;
import com.zhiqin.zhiqinpicturebackend.collaboration.interfaces.PictureEditMessageTypeEnum;
import com.zhiqin.zhiqinpicturebackend.collaboration.interfaces.PictureEditRequestMessage;
import com.zhiqin.zhiqinpicturebackend.collaboration.interfaces.PictureEditResponseMessage;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {
    @Resource
    private PictureEditHandler pictureEditHandler;

    @Resource
    private UserAppService UserAppService;

    @Override
    public void onEvent(PictureEditEvent pictureEditEvent) throws Exception {
        PictureEditRequestMessage pictureEditRequestMessage = pictureEditEvent.getPictureEditRequestMessage();
        WebSocketSession session = pictureEditEvent.getSession();
        User user = pictureEditEvent.getUser();
        Long pictureId = pictureEditEvent.getPictureId();
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.getEnumByValue(type);
        switch (pictureEditMessageTypeEnum) {
            case ENTER_EDIT:
                pictureEditHandler.handleEnterEditMessage(pictureEditRequestMessage,session,user,pictureId);
                break;
            case EDIT_ACTION:
                pictureEditHandler.handleEditActionMessage(pictureEditRequestMessage,session,user,pictureId);
                break;
            case EXIT_EDIT:
                pictureEditHandler.handleExitEditMessage(pictureEditRequestMessage,session,user,pictureId);
                break;
            default:
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                pictureEditResponseMessage.setMessage("消息类型错误");
                pictureEditResponseMessage.setUser(UserAppService.getUserVo(user));
                session.sendMessage(new TextMessage(PictureEditMessageJson.toJson(pictureEditResponseMessage)));
        }


    }
}
