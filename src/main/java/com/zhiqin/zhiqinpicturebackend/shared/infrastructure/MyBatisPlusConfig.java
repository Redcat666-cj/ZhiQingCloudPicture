package com.zhiqin.zhiqinpicturebackend.shared.infrastructure;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.zhiqin.zhiqinpicturebackend.picture.infrastructure.sharding.PictureTableNameHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({        // identity 上下文（DDD：Mapper 收在 infrastructure 包里）
        "com.zhiqin.zhiqinpicturebackend.identity.infrastructure",
        // space 上下文
        "com.zhiqin.zhiqinpicturebackend.space.infrastructure",
        // picture 上下文（旧 mapper 包仍在，阶段 3b 删除后会去掉第一行）
        "com.zhiqin.zhiqinpicturebackend.picture.infrastructure"
})
public class MyBatisPlusConfig {

    /**
     * 拦截器配置
     *
     * @return {@link MybatisPlusInterceptor}
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(PictureTableNameHandler pictureTableNameHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 动态表名插件：把逻辑表 picture 改写成 picture_public / picture_{spaceId}
        // 必须加在分页插件之前，先改表名再拼 limit
        interceptor.addInnerInterceptor(new DynamicTableNameInnerInterceptor(pictureTableNameHandler));
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
