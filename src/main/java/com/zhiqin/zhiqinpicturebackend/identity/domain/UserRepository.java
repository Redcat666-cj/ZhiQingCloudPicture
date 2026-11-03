package com.zhiqin.zhiqinpicturebackend.identity.domain;

import com.zhiqin.zhiqinpicturebackend.shared.domain.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * 用户仓储（领域层定义的端口）。
 *
 * <p>实现放在 {@code identity.infrastructure.UserRepositoryImpl}，内部仍用 MyBatis-Plus，
 * 但领域层与应用层完全看不到 Mapper / QueryWrapper。</p>
 */
public interface UserRepository {

    /**
     * 按主键查询（逻辑删除的记录视为不存在）
     */
    User findById(Long id);

    /**
     * 批量按主键查询
     */
    List<User> findByIds(Collection<Long> ids);

    /**
     * 按账号查询（用于注册查重）
     */
    User findByAccount(String userAccount);

    /**
     * 按账号 + 密文密码查询（用于登录）
     */
    User findByAccountAndPassword(String userAccount, String encryptedPassword);

    /**
     * 账号是否已存在
     */
    boolean existsByAccount(String userAccount);

    /**
     * 新增，成功后把自增/回填的主键写回传入对象
     */
    User save(User user);

    /**
     * 按主键更新非空字段
     */
    boolean update(User user);

    /**
     * 按主键逻辑删除
     */
    boolean removeById(Long id);

    /**
     * 条件分页查询
     */
    PageResult<User> page(UserQuery query);
}
