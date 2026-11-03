package com.zhiqin.zhiqinpicturebackend.collaboration.interfaces;
import cn.hutool.json.JSONUtil;
import com.zhiqin.zhiqinpicturebackend.identity.application.dto.UserVO;

import cn.hutool.core.collection.CollUtil;

import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.collaboration.infrastructure.PictureEditEventProducer;
import com.zhiqin.zhiqinpicturebackend.collaboration.interfaces.PictureEditActionEnum;
import com.zhiqin.zhiqinpicturebackend.collaboration.interfaces.PictureEditMessageTypeEnum;
import com.zhiqin.zhiqinpicturebackend.collaboration.interfaces.PictureEditRequestMessage;
import com.zhiqin.zhiqinpicturebackend.collaboration.interfaces.PictureEditResponseMessage;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Component

public class PictureEditHandler extends TextWebSocketHandler {
    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    // 当前编辑者已经执行过的编辑动作，key: pictureId，用于给「后加入的客户端」同步当前视图
    // （协议只传递增量动作，没有状态快照，不同步的话后来者会一直看到初始视图）
    private final Map<Long, List<String>> pictureEditActions = new ConcurrentHashMap<>();

    private final UserAppService UserAppService;

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;

    public PictureEditHandler(UserAppService UserAppService) {
        this.UserAppService = UserAppService;
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
            // 统一用 PictureEditMessageJson 序列化：Long 转字符串，避免雪花 id 在前端丢精度
            TextMessage textMessage = new TextMessage(PictureEditMessageJson.toJson(pictureEditResponseMessage));
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
        // 注意：这里必须用 pictureId 作为 key，并且先建好集合，否则 get() 返回 null 会 NPE，
        // 连接刚建立就会被 ExceptionWebSocketHandlerDecorator 以 1011 关闭
        pictureSessions.computeIfAbsent(pictureId, key -> ConcurrentHashMap.newKeySet()).add(session);
        // 注意：不要把「刚连上的用户」直接置为正在编辑，
        // 否则任何人都进不去编辑态（handleEnterEditMessage 里的判断永远不成立），编辑权必须由 ENTER_EDIT 显式申请

        //构造响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 已经加入编辑图片 %d", user.getUserName(), pictureId);
        pictureEditResponseMessage.setMessage(message);
        UserVO userVo = UserAppService.getUserVo(user);
        pictureEditResponseMessage.setUser(userVo);
        broadcastToPicture(pictureId, pictureEditResponseMessage);

        // 把「当前谁正在编辑」同步给这个新连接：
        // ENTER_EDIT 只在有人抢占编辑权的那一刻广播，后来加入的客户端收不到历史消息，
        // 不同步的话它会以为没人在编辑（点「开始编辑」才被拒绝）
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null) {
            User editingUser = UserAppService.getById(editingUserId);
            if (editingUser != null) {
                PictureEditResponseMessage enterEditMessage = new PictureEditResponseMessage();
                enterEditMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
                enterEditMessage.setMessage(String.format("用户 %s 正在编辑图片 %d", editingUser.getUserName(), pictureId));
                enterEditMessage.setUser(UserAppService.getUserVo(editingUser));
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(PictureEditMessageJson.toJson(enterEditMessage)));
                }
                // 再把编辑者已经做过的动作按顺序补发给这个新连接，让它看到和编辑者一样的视图。
                // 只发给新连接（不是广播），message 留空⇒前端静默应用，不往消息列表里刷屏
                List<String> actions = pictureEditActions.get(pictureId);
                if (actions != null) {
                    for (String editAction : actions) {
                        PictureEditResponseMessage syncMessage = new PictureEditResponseMessage();
                        syncMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
                        syncMessage.setEditAction(editAction);
                        syncMessage.setUser(UserAppService.getUserVo(editingUser));
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(PictureEditMessageJson.toJson(syncMessage)));
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        //将消息解析为PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        //从Session属性中获取公共参数
        Map<String, Object> attributes = session.getAttributes();
        User user =(User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 未知类型不能直接 valueOf，否则抛 IllegalArgumentException 会把连接打挂，这里回一条 ERROR 给发送方
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum =
                PictureEditMessageTypeEnum.getEnumByValue(pictureEditRequestMessage.getType());
        if (pictureEditMessageTypeEnum == null) {
            PictureEditResponseMessage errorMessage = new PictureEditResponseMessage();
            errorMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
            errorMessage.setMessage("消息类型错误");
            errorMessage.setUser(UserAppService.getUserVo(user));
            session.sendMessage(new TextMessage(PictureEditMessageJson.toJson(errorMessage)));
            return;
        }
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage,session,user,pictureId);
    }

    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, @NonNull WebSocketSession session, User user, Long pictureId) throws IOException {
        //没有用户正在编辑该图片，才能进入编辑
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null) {
            // 已经有人（或自己）在编辑：回一条 ERROR 给申请方，前端才能提示「谁正在编辑」
            if (editingUserId.equals(user.getId())) {
                return;
            }
            User editingUser = UserAppService.getById(editingUserId);
            PictureEditResponseMessage errorMessage = new PictureEditResponseMessage();
            errorMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
            errorMessage.setMessage(String.format("用户 %s 正在编辑该图片，请等待他完成编辑",
                    editingUser == null ? "其他用户" : editingUser.getUserName()));
            errorMessage.setUser(UserAppService.getUserVo(editingUser));
            session.sendMessage(new TextMessage(PictureEditMessageJson.toJson(errorMessage)));
            return;
        }
        //设置当前用户为编辑用户
        pictureEditingUsers.put(pictureId, user.getId());
        // 新的编辑者从「初始视图」开始，清掉上一位编辑者的动作记录
        pictureEditActions.put(pictureId, new CopyOnWriteArrayList<>());
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
        String message = String.format("用户 %s 开始编辑图片 %d", user.getUserName(), pictureId);
        pictureEditResponseMessage.setMessage(message);
        UserVO userVo = UserAppService.getUserVo(user);
        pictureEditResponseMessage.setUser(userVo);
        broadcastToPicture(pictureId, pictureEditResponseMessage);
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
            pictureEditResponseMessage.setUser(UserAppService.getUserVo(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
            // 记下来，后面有人加进来时用它同步视图
            pictureEditActions.computeIfAbsent(pictureId, key -> new CopyOnWriteArrayList<>()).add(editAction);
        }
    }

    //用户退出编辑操作时，移除当前用户的编辑状态，并且向其‌他客户‌端发送‌消息：
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if(editingUserId != null&&editingUserId.equals(user.getId())) {
            pictureEditingUsers.remove(pictureId);
            // 编辑结束，动作记录一起清掉，下一位编辑者重新从初始视图开始
            pictureEditActions.remove(pictureId);
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("用户 %s 已经退出编辑图片 %d", user.getUserName(), pictureId);
            pictureEditResponseMessage.setMessage(message);
            UserVO userVo = UserAppService.getUserVo(user);
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
        pictureEditResponseMessage.setUser(UserAppService.getUserVo(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

}
