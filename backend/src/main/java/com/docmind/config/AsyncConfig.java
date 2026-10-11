package com.docmind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务配置。
 *
 * 文档上传后的"解析→分块→向量化→打标"流水线比较耗时（尤其 OCR），
 * 绝不能在 HTTP 请求线程里同步执行，否则页面会一直转圈。
 * 做法：上传接口只负责保存文件和建记录，然后把处理任务丢进这个线程池异步执行。
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean("docProcessExecutor")
    public ThreadPoolTaskExecutor docProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 个人使用场景：同时处理的文档不多，2~4 个线程足够，多了反而抢 CPU
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("doc-process-");
        // 线程池关闭时等待任务跑完，避免应用停止时文档状态卡在"处理中"
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
