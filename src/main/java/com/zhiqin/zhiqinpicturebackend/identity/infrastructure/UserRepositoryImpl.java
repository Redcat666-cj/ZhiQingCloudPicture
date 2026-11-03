package com.zhiqin.zhiqinpicturebackend.identity.infrastructure;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserQuery;
import com.zhiqin.zhiqinpicturebackend.identity.domain.UserRepository;
import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户仓储实现：负责 PO ↔ 领域对象转换、把 {@link UserQuery} 翻译成 QueryWrapper。
 *
 * <p>这是 MyBatis-Plus 唯一被允许"露头"的地方；领域层与应用层只看到 {@link UserRepository}。</p>
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    @Resource
    private UserMapper userMapper;

    @Override
    public User findById(Long id) {
        if (id == null) {
            return null;
        }
        return toDomain(userMapper.selectById(id));
    }

    @Override
    public List<User> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return userMapper.selectBatchIds(ids).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public User findByAccount(String userAccount) {
        if (StrUtil.isBlank(userAccount)) {
            return null;
        }
        QueryWrapper<UserPO> wrapper = new QueryWrapper<>();
        wrapper.eq("userAccount", userAccount);
        List<UserPO> list = userMapper.selectList(wrapper);
        return list.isEmpty() ? null : toDomain(list.get(0));
    }

    @Override
    public User findByAccountAndPassword(String userAccount, String encryptedPassword) {
        QueryWrapper<UserPO> wrapper = new QueryWrapper<>();
        wrapper.eq("userAccount", userAccount);
        wrapper.eq("userPassword", encryptedPassword);
        List<UserPO> list = userMapper.selectList(wrapper);
        return list.isEmpty() ? null : toDomain(list.get(0));
    }

    @Override
    public boolean existsByAccount(String userAccount) {
        QueryWrapper<UserPO> wrapper = new QueryWrapper<>();
        wrapper.eq("userAccount", userAccount);
        return userMapper.selectCount(wrapper) > 0;
    }

    @Override
    public User save(User user) {
        UserPO po = toPO(user);
        userMapper.insert(po);
        // MyBatis-Plus 主键回填，必须带回领域对象，否则上层拿不到 id
        user.setId(po.getId());
        return user;
    }

    @Override
    public boolean update(User user) {
        return userMapper.updateById(toPO(user)) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return userMapper.deleteById(id) > 0;
    }

    @Override
    public PageResult<User> page(UserQuery query) {
        QueryWrapper<UserPO> wrapper = new QueryWrapper<>();
        wrapper.eq(query.getId() != null, "id", query.getId());
        wrapper.eq(StrUtil.isNotBlank(query.getUserRole()), "userRole", query.getUserRole());
        wrapper.like(StrUtil.isNotBlank(query.getUserAccount()), "userAccount", query.getUserAccount());
        wrapper.like(StrUtil.isNotBlank(query.getUserName()), "userName", query.getUserName());
        wrapper.like(StrUtil.isNotBlank(query.getUserProfile()), "userProfile", query.getUserProfile());
        if (StrUtil.isNotEmpty(query.getSortField())) {
            wrapper.orderBy(true, query.isAscend(), query.getSortField());
        }
        Page<UserPO> page = userMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        List<User> records = page.getRecords().stream().map(this::toDomain).collect(Collectors.toList());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private User toDomain(UserPO po) {
        if (po == null) {
            return null;
        }
        User user = new User();
        BeanUtil.copyProperties(po, user);
        return user;
    }

    private UserPO toPO(User user) {
        UserPO po = new UserPO();
        BeanUtil.copyProperties(user, po);
        return po;
    }
}
