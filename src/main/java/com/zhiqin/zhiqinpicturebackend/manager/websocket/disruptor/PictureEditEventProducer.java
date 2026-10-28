package com.zhiqin.zhiqinpicturebackend.manager.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.manager.websocket.model.PictureEditRequestMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;

@Component
@Slf4j
public class PictureEditEventProducer {

    private final Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    public PictureEditEventProducer(Disruptor<PictureEditEvent> pictureEditEventDisruptor) {
        this.pictureEditEventDisruptor = pictureEditEventDisruptor;
    }

    public void publishEvent (PictureEditRequestMessage pictureEditRequestMessage,
                              WebSocketSession session, User user, Long pictureId) {

        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        //获取可以生成的位置
        long next = ringBuffer.next();
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        //发布事件
        ringBuffer.publish(next);

    }



    /*
    * 优雅停机 当队列环中位置全部已被占满 停止发出新事件
    * */
   @PreDestroy
    public void close() {
        pictureEditEventDisruptor.shutdown();
    }

}
