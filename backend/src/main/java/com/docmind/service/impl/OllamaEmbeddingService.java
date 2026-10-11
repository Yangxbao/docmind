package com.docmind.service.impl;

import com.docmind.common.BizException;
import com.docmind.service.EmbeddingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama 本地模型向量化（可选实现，真语义检索）。
 *
 * Ollama 是免费开源的本地大模型运行器，数据完全不出本机：
 *   1. 安装 Ollama（官网下载 Windows 版一键安装）；
 *   2. 拉取中文效果好的免费嵌入模型：ollama pull bge-m3（约 1.2GB，一次性）；
 *   3. 把 application.yml 里 docmind.embedding.provider 改成 ollama，重启即可。
 *
 * 本类只是简单地对 http://localhost:11434/api/embeddings 发一个 HTTP POST，
 * 没有任何 SDK 依赖，方便理解和调试。切换模型后记得在页面上点"重建索引"。
 */
@Service
@ConditionalOnProperty(name = "docmind.embedding.provider", havingValue = "ollama")
public class OllamaEmbeddingService implements EmbeddingService {

    private final RestTemplate restTemplate;

    @Value("${docmind.ollama.base-url}")
    private String baseUrl;

    @Value("${docmind.ollama.model}")
    private String model;

    /** 第一次成功调用后记录真实维度（不同模型维度不同，bge-m3 是 1024） */
    private volatile Integer cachedDim;

    public OllamaEmbeddingService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);    // 连接 5 秒超时：Ollama 没启动时快速失败
        factory.setReadTimeout(60_000);      // 读取 60 秒超时：批量文本向量化留足时间
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public int dim() {
        if (cachedDim == null) {
            throw new BizException("向量服务尚未完成首次调用（维度未知），请先上传文档或执行一次检索");
        }
        return cachedDim;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Float> embed(String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", text == null ? "" : text);
        try {
            Map<String, Object> resp = restTemplate.postForEntity(
                    baseUrl + "/api/embeddings", body, Map.class).getBody();
            if (resp == null || !(resp.get("embedding") instanceof List<?> rawList)) {
                throw new BizException("Ollama 返回数据格式异常，请检查模型名是否正确: " + model);
            }
            cachedDim = rawList.size();
            return ((List<Number>) rawList).stream()
                    .map(Number::floatValue)
                    .toList();
        } catch (RestClientException e) {
            // 最常见的失败：Ollama 没启动 / 模型没拉取。给出可操作的提示
            throw new BizException("本地向量服务(Ollama)调用失败: " + e.getMessage()
                    + "。请确认: 1) 已启动 Ollama  2) 已执行 ollama pull " + model);
        }
    }
}
