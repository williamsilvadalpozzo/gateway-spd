package com.api.gateway.config;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    @Bean(name = "processoExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor processoExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                8,
                32,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(200),
                nomearThreads(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private ThreadFactory nomearThreads() {
        final AtomicInteger sequencia = new AtomicInteger();
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "gateway-processo-" + sequencia.incrementAndGet());
                thread.setDaemon(false);
                return thread;
            }
        };
    }
}
