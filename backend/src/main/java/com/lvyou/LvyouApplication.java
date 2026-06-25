package com.lvyou;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 旅游路线规划系统 — 唯一启动器
 * <p>
 * 扫描 com.lvyou 包，自动加载 backend 和 agent-service 两个模块的所有 Bean。
 * agent-service 作为纯库模块，不独立启动，由本启动器统一加载。
 */
@SpringBootApplication(scanBasePackages = "com.lvyou")
public class LvyouApplication {

    public static void main(String[] args) {
        SpringApplication.run(LvyouApplication.class, args);
    }
}
