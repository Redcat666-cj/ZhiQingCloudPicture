package com.zhiqin.zhiqinpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhiqin.zhiqinpicturebackend.domain.entity.Space;
import com.zhiqin.zhiqinpicturebackend.domain.entity.SpaceUser;
import com.zhiqin.zhiqinpicturebackend.domain.entity.User;
import com.zhiqin.zhiqinpicturebackend.domain.enums.SpaceLevelEnum;
import com.zhiqin.zhiqinpicturebackend.domain.enums.SpaceRoleEnum;
import com.zhiqin.zhiqinpicturebackend.domain.enums.SpaceTypeEnum;
import com.zhiqin.zhiqinpicturebackend.domain.space.SpaceAddRequest;
import com.zhiqin.zhiqinpicturebackend.domain.space.SpaceQueryRequest;
import com.zhiqin.zhiqinpicturebackend.domain.vo.SpaceVO;
import com.zhiqin.zhiqinpicturebackend.domain.vo.UserVO;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.exception.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.manager.auth.SpaceUserAuthManager;
import com.zhiqin.zhiqinpicturebackend.manager.table.SpaceTableManager;
import com.zhiqin.zhiqinpicturebackend.service.SpaceService;
import com.zhiqin.zhiqinpicturebackend.mapper.SpaceMapper;
import com.zhiqin.zhiqinpicturebackend.service.SpaceUserService;
import com.zhiqin.zhiqinpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
* @author 崔健
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2026-10-03 12:53:02
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{
    @Resource
    private UserService userService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private SpaceTableManager spaceTableManager;
    // 与 SpaceUserServiceImpl 里的 SpaceService 形成互相依赖，用 @Lazy 断开
    @Resource
    @Lazy
    private SpaceUserService spaceUserService;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    ConcurrentHashMap<Long,Object> lockMap = new ConcurrentHashMap<>();

    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if(spaceAddRequest.getSpaceType() == null){
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 空间归属为当前登录用户
        Long userId = loginUser.getId();
        space.setUserId(userId);
        // 数据校验
        validSpace(space, true);
        // 根据空间级别自动填充限额
        fillSpaceBySpecialLevel(space);
        // 权限校验
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 根据用户 id 加锁：intern 保证同一用户拿到同一把锁，同一用户串行创建，不同用户互不影响
        //但是我们是用字符常量池进行加锁的,数据不会及时释放,可以用ConcurrentHashMap进行本地锁优化 ->或
        // 分布式锁
        Object lock = lockMap.computeIfAbsent(userId, key -> new Object());
        synchronized (lock) {
            // 编程式事务必须在锁内：若用注解式事务，锁在事务提交前就已释放，并发下后一个请求查不到未提交数据，仍会重复创建
            Long spaceId = transactionTemplate.execute(status -> {
                // 每个用户每种类型只能拥有一个空间
                // 注意用 space.getSpaceType()（已填过默认值），请求参数里没传时它为 null，eq 条件会失效
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId,userId)
                        .eq(Space::getSpaceType,space.getSpaceType())
                        .exists();
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户最多只能创建一个空间");
                try{
                    // 操作数据库
                    boolean result = this.save(space);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                    // 团队空间：创建者默认是空间管理员，否则创建完自己没有成员管理权限
                    if (Integer.valueOf(SpaceTypeEnum.TEAM.getValue()).equals(space.getSpaceType())) {
                        SpaceUser spaceUser = new SpaceUser();
                        spaceUser.setSpaceId(space.getId());
                        spaceUser.setUserId(userId);
                        spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                        boolean saved = spaceUserService.save(spaceUser);
                        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);
                    }
                }finally {
                    lockMap.remove(userId);
                }

                return space.getId();
            });
            // 动态分表：新空间必须有自己的图片表 picture_{spaceId}。
            // 建表是 DDL（MySQL 里会隐式提交），所以放在事务提交之后再执行
            spaceTableManager.ensureSpaceTable(spaceId);
            return spaceId;
            // 返回结果是包装类，可以做一些处理

        }
    }


    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        //拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "name", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        //排序
        queryWrapper.orderBy(ObjUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVo(user);
            spaceVO.setUser(userVO);
        }
        // 填充当前用户在该空间内的权限列表，前端据此控制按钮显隐
        User loginUser = null;
        if (request != null) {
            try {
                loginUser = userService.getLoginUser(request);
            } catch (Exception ignored) {
                // 未登录时权限列表为空即可，不在这里抛异常
            }
        }
        spaceVO.setPermissionList(spaceUserAuthManager.getPermissionList(space, loginUser));
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVo(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public void validSpace(Space space,boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum enumByValue = SpaceTypeEnum.getEnumByValue(spaceType);
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 要创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            if (enumByValue == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间类别不能为空");

            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
        if(spaceType != null&&enumByValue == null ){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间类型不存在");
        }
    }

    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 空间创建者或管理员可访问。Long 用 equals 比较，避免值相同但对象不同时误判
        boolean isOwner = loginUser.getId() != null && loginUser.getId().equals(space.getUserId());
        if (!isOwner && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    @Override
    public void fillSpaceBySpecialLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum enumByValue = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (enumByValue != null) {
            long maxSize = enumByValue.getMaxSize();
            if(space.getMaxSize() == null ) {
                space.setMaxSize(maxSize);
            }
            long maxCount = enumByValue.getMaxCount();
            if(space.getMaxCount() == null ) {
                space.setMaxCount(maxCount);
            }//如果空间本身没有设置限额，才会自动填充，保证了灵活性。
        }
    }
}




