package com.lvyou.agent.config;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope Agent 配置
 * <p>
 * 配置项定义在 agent-service 模块的 application-agent.yml 中，
 * 由 backend 启动器通过 spring.config.import 统一加载。
 * <p>
 * 支持三种 LLM 提供商：
 * <ul>
 *   <li><b>dashscope</b> — 阿里云 DashScope（通义千问），默认</li>
 *   <li><b>openai</b> — OpenAI 官方 API</li>
 *   <li><b>local</b> — 本地部署的 OpenAI 兼容服务（Ollama / vLLM / LM Studio 等）</li>
 * </ul>
 */
@Slf4j
@Configuration
public class AgentConfig {

    @Value("${llm.provider:dashscope}")
    private String provider;

    @Value("${llm.url}")
    private String url;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String modelName;

    /**
     * 根据 provider 创建对应的 ChatModel Bean
     */
    @Bean
    public io.agentscope.core.model.Model chatModel() {
        log.info("初始化 LLM 模型: provider={}, url={}, model={}", provider, url, modelName);

        return switch (provider.toLowerCase()) {
            case "dashscope" -> DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .build();

            case "openai" -> OpenAIChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .baseUrl(url)
                    .build();

            case "local" -> OpenAIChatModel.builder()
                    .apiKey(apiKey != null && !apiKey.isBlank() ? apiKey : "ollama")
                    .modelName(modelName)
                    .baseUrl(url)
                    .build();

            default -> {
                log.warn("未知的 LLM provider: {}，回退为 DashScope", provider);
                yield DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(modelName)
                        .build();
            }
        };
    }

    @Bean
    public Toolkit toolkit() {
        return new Toolkit();
    }
}
