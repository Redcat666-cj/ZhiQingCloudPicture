package com.zhiqin.zhiqinpicturebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
/*通过 Spring AOP 提供对当前代理对象的访问，使得可以在业务逻辑中访问到当前的代理对象。
你可以在方法执行时通过 AopContext.currentProxy() 获取当前的代理对象。*/

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy(exposeProxy = true)
public class ZhiqinPictureBackendApplication {

    public static void main(String[] args) {

        SpringApplication.run(ZhiqinPictureBackendApplication.class, args);
    }

}
