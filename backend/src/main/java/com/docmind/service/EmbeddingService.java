package com.docmind.service;

import java.util.List;

/**
 * 向量化接口（本项目的关键解耦点）。
 *
 * "向量化"就是把一段文字变成一个固定长度的浮点数组：
 * 文字意思越接近，向量的方向越接近（余弦相似度越高），这就是语义检索的基础。
 *
 * 为什么做成接口？因为在"隐私、零成本、开箱即用"的约束下有两条路：
 *   - TfidfEmbeddingService：纯 Java 内置词频向量，零依赖保证一键跑通（词级相似度）；
 *   - OllamaEmbeddingService：调用本地 Ollama 模型（bge-m3），真语义检索，仍然免费、仍然本地。
 * 两者通过 application.yml 的 docmind.embedding.provider 一行配置切换，
 * 未来要换其他模型（哪怕接别的服务）也只需要新增一个实现类，业务代码完全不用动。
 */
public interface EmbeddingService {

    /** 当前实现的向量维度（内置词频向量 512；bge-m3 是 1024） */
    int dim();

    /**
     * 把一段文字变成向量。
     * @param text 原始文本（一个分块或一次搜索的查询词）
     * @return 固定维度的向量，已归一化
     */
    List<Float> embed(String text);
}
