package com.docmind;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docmind.entity.User;
import com.docmind.mapper.UserMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * DocMind 智档 · 启动入口
 *
 * 项目分层：
 *   controller  接口层：接收请求、参数校验、调用 Service、返回统一 Result
 *   service     业务层：文档解析/分块/向量化/检索/标签等所有业务逻辑
 *   mapper      数据层：MyBatis-Plus 的 BaseMapper，一般不用手写 SQL
 *   common      通用层：统一返回体、全局异常、当前用户上下文、停用词表
 */
@SpringBootApplication
@MapperScan("com.docmind.mapper")
public class DocmindApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocmindApplication.class, args);
    }

    /**
     * 首次启动时自动创建测试账号 admin / admin123，方便开箱即用。
     * （不放 SQL 里是因为 BCrypt 每次生成的哈希都不同，在代码里生成更直观）
     */
    @Bean
    public ApplicationRunner seedAdminUser(UserMapper userMapper) {
        return args -> {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
            if (count == null || count == 0) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(BCrypt.hashpw("admin123"));
                admin.setNickname("管理员");
                userMapper.insert(admin);
                System.out.println(">>> 已自动创建测试账号：admin / admin123");
            }
        };
    }
}
