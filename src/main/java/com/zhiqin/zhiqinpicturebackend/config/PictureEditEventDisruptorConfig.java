package com.zhiqin.zhiqinpicturebackend.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.lmax.disruptor.dsl.Disruptor;
import com.zhiqin.zhiqinpicturebackend.manager.websocket.disruptor.PictureEditEvent;
import com.zhiqin.zhiqinpicturebackend.manager.websocket.disruptor.PictureEditEventWorkHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import javax.annotation.Resource;

@Configuration
public class PictureEditEventDisruptorConfig {

    @Resource
    @Lazy
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;


    @Bean("pictureEditEventDisruptor")
    public Disruptor<PictureEditEvent> messageModelRingBuffer(){
        //ringBuffer大小
        int bufferSize = 1024*256;
        Disruptor<PictureEditEvent> messageModelRingBuffer = new Disruptor<>(
                PictureEditEvent::new,
                bufferSize,
                ThreadFactoryBuilder.create().setNamePrefix("pictureEditEventDisruptor").build()
        );

        //设置消费者
        messageModelRingBuffer.handleEventsWithWorkerPool(pictureEditEventWorkHandler);
        //开启disruptor
        messageModelRingBuffer.start();
        return messageModelRingBuffer;
    }
}
