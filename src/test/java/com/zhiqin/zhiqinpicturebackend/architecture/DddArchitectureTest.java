package com.zhiqin.zhiqinpicturebackend.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * DDD 分层架构守护测试。
 *
 * <p>重构之后由 {@code mvn test} 自动卡住依赖方向，防止后来又拧回 MVC 的样子。</p>
 *
 * <p>这里只写"当前已成立且值得长期守"的规则。还有两条想守但当前不成立的
 * （接口层仍用 MyBatis-Plus 的 Page 作响应载体、协作上下文的 WS 管道
 * infrastructure 反向依赖 interfaces），已记录在
 * {@code docs/ddd-architecture.md} 的技术债清单里，等下一步收口。</p>
 */
@AnalyzeClasses(packages = "com.zhiqin.zhiqinpicturebackend")
public class DddArchitectureTest {

    /**
     * 规则 1：领域层必须是纯的
     *
     * <p>domain 不许依赖 Spring、MyBatis、Servlet，也不许依赖基础设施、接口层、应用层。
     * 领域模型只认识自己、JDK 和 shared.domain 的基础类型。</p>
     */
    @ArchTest
    static final ArchRule domain_should_be_pure = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.baomidou..",
                    "javax.servlet..",
                    "jakarta.servlet..",
                    "..infrastructure..",
                    "..interfaces..",
                    "..application.."
            )
            .because("领域层不应该依赖框架、接口层或应用层");

    /**
     * 规则 2：MVC 时代的旧包不许回流
     */
    @ArchTest
    static final ArchRule legacy_packages_should_be_gone = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.zhiqin.zhiqinpicturebackend.manager..",
                    "com.zhiqin.zhiqinpicturebackend.controller..",
                    "com.zhiqin.zhiqinpicturebackend.utils..",
                    "com.zhiqin.zhiqinpicturebackend.config..",
                    "com.zhiqin.zhiqinpicturebackend.api..",
                    "com.zhiqin.zhiqinpicturebackend.domain.entity..",
                    "com.zhiqin.zhiqinpicturebackend.domain.dto..",
                    "com.zhiqin.zhiqinpicturebackend.domain.vo.."
            )
            .because("这些是重构前的分层包，已经全部迁入对应上下文");

    /**
     * 规则 3：领域层不许依赖应用层（端口的数据契约应放在 domain）
     */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_application = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .because("端口与其数据契约同层，方向只能是 application -> domain");
}
