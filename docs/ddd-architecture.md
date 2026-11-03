# DDD 架构说明（重构后）

> 本文件描述 2026-11 重构后的分层与边界。重构前是标准 MVC 三层（controller / service / mapper）。

## 1. 上下文地图

代码按业务能力切成 8 个包，每个包内部都是一套完整的四层：

| 上下文 | 职责 | 主要聚合 / 端口 |
|---|---|---|
| `identity` | 注册登录、用户档案、角色、登录态 | `User`（聚合根）、`EncryptedPassword`（值对象）、`UserRepository` |
| `space` | 空间生命周期、配额、成员与角色、空间权限 | `Space`（聚合根）、`SpaceQuota`（值对象）、`SpaceUser`（独立聚合）、`SpaceRepository`、`SpaceMemberRepository`、`SpacePermissionAppService` |
| `picture` | 图片上传/审核/编辑/删除/查询、**动态分表路由** | `Picture`（聚合根，含审核状态机）、`PictureTableRouter`（端口）、`PictureRepository` |
| `analytics` | 空间用量/分类/标签/大小/趋势/排行（只读） | 无聚合，直接从仓储取数后内存聚合 |
| `ai` | 扩图、以图搜图、Bing 抓图 | `OutPaintingPort`、`ImageSearchPort`、`OutPaintingSizeCalculator`（领域规则） |
| `storage` | 对象存储（COS）上传/删除、元数据提取 | `StorageService`（端口）、`CosStorageService` |
| `collaboration` | WebSocket 协同编辑、Disruptor 事件 | `PictureEditHandler` 等 |
| `shared` | 统一返回、异常、分页、MyBatis/Redis/Jackson/Web 配置 | `PageResult`、`BusinessException`、`ErrorCode` |

## 2. 分层与依赖规则

```
interfaces  →  application  →  domain  ←  infrastructure
（controller/VO/DTO/注解）  （用例编排/事务）  （聚合/值对象/端口）  （仓储实现/外部 API 适配）
```

- `domain`：纯 POJO，不含 MyBatis 注解，不认识 Spring；持久化模型在 `infrastructure/*PO`
- `application`：只做"校验 → 调领域/仓储 → 组装输出"，`@Transactional` 只允许出现在这一层
- `infrastructure`：实现 domain 端口（Repository / StorageService / 外部 API 客户端）
- `interfaces`：参数校验、DTO ↔ 命令、VO 装配

依赖规则由 `src/test/java/.../architecture/DddArchitectureTest.java` 用 ArchUnit 守护，`mvn test` 会卡：

1. `domain` 不得依赖 Spring / MyBatis / Servlet / infrastructure / interfaces / application
2. MVC 时代的旧包（`manager`、`controller`、`utils`、`config`、`api`、`domain.entity|dto|vo`）不得回流
3. `domain` 不得依赖 `application`（端口的数据契约与端口同层）

## 3. 几个关键设计

### 3.1 动态分表（每个空间一张表）

逻辑表 `picture` → 物理表 `picture_public`（公共图库）/ `picture_{spaceId}`。

- 领域端口：`picture/domain/PictureTableRouter`（`locateSpaceId` / `inSpace` / `ensureSpaceTable` / `allScopes`）
- 实现：`picture/infrastructure/sharding/*`（ThreadLocal + MyBatis 动态表名插件 + 建表）
- **ThreadLocal 只允许出现在 infrastructure**，应用层通过端口表达"在某个空间里做事"
- 新建空间时在事务提交后建分表（DDL 会隐式提交，不能进事务）
- 请求级兜底：`picture/interfaces/SpaceTableContextFilter` 按 `id` 反查空间，保证只带图片 id 的接口也能路由

### 3.2 权限（Sa-Token）

- 配置：`biz/spaceAuthConfig.json`（角色 → 权限码）
- 领域/应用：`space/application/SpacePermissionAppService`（`getPermissions` / `getPermissionList`）
- 适配器：`space/infrastructure/StpInterfaceImpl`（把 Sa-Token 回调翻译成应用层查询）
- 声明式注解：`space/interfaces/annotation/@SaSpaceCheckPermission`

### 3.3 跨聚合一致性

"上传图片扣空间配额"是一个本地事务：`PictureAppService` 内 `保存图片 + SpaceAppService.changeUsage(+size, +count)`；
覆盖上传只补差值，删除时回补。没有引入分布式事务 / 领域事件。

## 4. 重构成果（MVC → DDD）

| 指标 | 重构前 | 重构后 |
|---|---|---|
| 顶层结构 | controller / service / mapper / domain(数据袋) | 8 个上下文 × 四层 |
| 最大单类 | `PictureServiceImpl` 911 行 | `PictureAppServiceImpl` 约 700 行（待继续拆分，见技术债） |
| MyBatis 泄漏 | entity 带 `@TableName` 暴露给前端、service 继承 `IService` | 实体零注解、应用服务不继承 MP、Mapper 收在 infrastructure |
| 领域行为 | 全在 service | `Picture.review()` 状态机、`Space.validate()`、`SpaceQuota.ensureCanHold()`、`EncryptedPassword` |
| 架构守护 | 无 | ArchUnit 3 条规则 |

## 5. 技术债清单（下一步收口）

1. **`UserAppService.getLoginUser(HttpServletRequest)`**：应用层仍依赖 Servlet API（29 处调用），应改为 interfaces 层的登录态解析器 + 应用层只收 `userId`
2. **接口层仍用 MyBatis-Plus `Page` 作响应载体**：为保持前端 JSON 契约不变（`records/total/size/current` 等字段）暂时保留，理想方案是自定义 `PageVO`
3. **`analytics` 内存聚合**：当前把命中行读入内存统计，数据量上来后应换成数据库侧聚合的查询 DAO
4. **`picture` 应用服务仍是单类**：可继续按用例拆成 Upload / Review / Edit / Delete / Query 五个服务
5. **`storage` 上传模板未彻底端口化**：`StorageService` 只做了适配层，压缩/缩略图等 CI 处理仍在模板里
6. **协作上下文 WS 管道**：`collaboration/infrastructure` 的 Disruptor 处理器反依赖 `collaboration/interfaces` 的 Handler（故未纳入 ArchUnit 规则）
7. **删除空间不会 drop `picture_{spaceId}`**：重构前就存在的业务缺口
8. **`FileController` 直接依赖 `CosManager`**：下载接口尚未走 `StorageService` 端口

## 6. 如何验证

```bash
mvn clean compile                 # 编译
mvn test -Dtest=DddArchitectureTest   # 分层依赖规则
# 启动后按控制器回归：
#   /user/**（11） /space/**（8） /spaceUser/**（6） /picture/**（19）
#   /space/analyze/**（6） /file/**（2） /health（1）  共 53 个端点
```

> 注：`/picture/upload*`、`/picture/out_painting/*`、`/file/*` 依赖腾讯云 COS / 阿里云百炼，回归时需要真实外部服务；
> 重构后已用 37 次真实请求覆盖其余全部端点，全部 `code=0`。
