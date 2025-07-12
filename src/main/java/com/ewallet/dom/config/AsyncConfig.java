package com.ewallet.dom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

   /*
    Core Pool Size: The minimum number of threads kept alive in the pool.
    Max Pool Size: The maximum number of threads allowed in the pool.
    Queue Capacity: The capacity of the queue used to hold tasks when all threads are busy.
    Thread Name Prefix: A prefix for the names of the threads created by the executor.
    */

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor localTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // Adjust as needed
        executor.setMaxPoolSize(10); // Adjust as needed
        executor.setQueueCapacity(250); // Adjust as needed
        executor.setThreadNamePrefix("eWalletThread-");
        executor.initialize();
        return executor;
    }

//    @Bean(name = "taskExecutor")
//    public DelegatingSecurityContextAsyncTaskExecutor delegatingSecurityContextAsyncTaskExecutor() {
//        return new DelegatingSecurityContextAsyncTaskExecutor(localTaskExecutor());
//    }
}
