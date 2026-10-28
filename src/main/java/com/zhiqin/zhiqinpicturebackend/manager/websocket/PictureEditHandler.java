package com.zhiqin.zhiqinpicturebackend.manager.websocket;
import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.domain.vo.UserVO;

import cn.hutool.core.collection.CollUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.zhiqin.zhiqinpicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.zhiqin.zhiqinpicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.zhiqin.zhiqinpicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.zhiqin.zhiqinpicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.zhiqin.zhiqinpicturebackend.service.UserService;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Component

public class PictureEditHandler extends TextWebSocketHandler {
    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();
    private final UserService userService;

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;

    public PictureEditHandler(UserService userService) {
        this.userService = userService;
    }

    /**
     * 考虑到可能同时会有多个websocket连接请求，使用ConcurrentHashMap来保证线程安全。
     * ->编写一个广播消息的方法,该方法会根据pictureId将响应消息发送编辑该图片
     * 的所有会话
     * 此外还要接受excludeSession参数支持排除某个会话发送消息
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage
            , WebSocketSession excludeSession) throws IOException {
        Set<WebSocketSession> sessions = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessions)) {
            //创建ObjectMapper
            //ObjectMapper是Jackson库中用于Java对象与JSON之间相互转换的核心类。
            ObjectMapper objectMapper = new ObjectMapper();
            //配置序列化:将Long类型转为String,解决精度丢失
            SimpleModule simpleModule = new SimpleModule();
            simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
            simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance); //支持long类型
            objectMapper.registerModule(simpleModule);
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessions) {
                //排除掉的session不发送
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }

        }

    }

    //全部广播
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws IOException {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    /**
     * 实现连接建立成功后执行的方法，保存会话到集合中，‌并且给‌其他会‌话发送消息：
     *
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        //保存到会话集合
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureEditingUsers.putIfAbsent(pictureId, user.getId());
        pictureSessions.get("pictureId").add(session);

        //构造响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 已经加入编辑图片 %d", user.getUserName(), pictureId);
        pictureEditResponseMessage.setMessage(message);
        UserVO userVo = userService.getUserVo(user);
        pictureEditResponseMessage.setUser(userVo);
        broadcastToPicture(pictureId, pictureEditResponseMessage);


    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        //将消息解析为PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.valueOf(type);
        //从Session属性中获取公共参数
        Map<String, Object> attributes = session.getAttributes();
        User user =(User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage,session,user,pictureId);
    }

    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, @NonNull WebSocketSession session, User user, Long pictureId) throws IOException {
        //没有用户正在编辑该图片，才能进入编辑
        if(!pictureEditingUsers.containsKey(pictureId)) {
            //设置当前用户为编辑用户
            pictureEditingUsers.put(pictureId, user.getId());
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("用户 %s 开始编辑图片 %d", user.getUserName(), pictureId);
            pictureEditResponseMessage.setMessage(message);
            UserVO userVo = userService.getUserVo(user);
            pictureEditResponseMessage.setUser(userVo);
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }





    }
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVo(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    //用户退出编辑操作时，移除当前用户的编辑状态，并且向其‌他客户‌端发送‌消息：
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if(editingUserId != null&&editingUserId.equals(user.getId())) {
            pictureEditingUsers.remove(pictureId);
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("用户 %s 已经退出编辑图片 %d", user.getUserName(), pictureId);
            pictureEditResponseMessage.setMessage(message);
            UserVO userVo = userService.getUserVo(user);
            pictureEditResponseMessage.setUser(userVo);
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }


    }

    /**
     * WebSocket 连接关闭时，需要移除当前用户的编辑状态、‌
     * 并且从集合中删除当前会话，还可以给其他客户端发送消息通知：
     */

    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus closeStatus) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        User user= (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        //移除编辑状态
       handleExitEditMessage(null,session,user,pictureId);
        //从集合中删除当前会话
        Set<WebSocketSession> sessions = pictureSessions.get(pictureId);
        if (sessions != null) {
            sessions.remove(session);
            if(sessions.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }
        //响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVo(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

}