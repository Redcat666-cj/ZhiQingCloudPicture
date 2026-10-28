package com.zhiqin.zhiqinpicturebackend.config;

import com.zhiqin.zhiqinpicturebackend.manager.websocket.HandshakeInterceptor;
import com.zhiqin.zhiqinpicturebackend.manager.websocket.PictureEditHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

@Configuration
@Slf4j
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
   @Resource
   private PictureEditHandler pictureEditHandler;

   @Resource
   private HandshakeInterceptor handshakeInterceptor;


    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        //websocket
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");


    }
}
