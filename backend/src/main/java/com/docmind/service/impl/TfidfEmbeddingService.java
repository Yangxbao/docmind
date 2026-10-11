package com.docmind.service.impl;

import com.docmind.common.StopWords;
import com.docmind.service.EmbeddingService;
import com.huaban.analysis.jieba.JiebaSegmenter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内置词频哈希向量化（默认实现，纯 Java、零外部依赖）。
 *
 * 算法步骤（都是可讲清楚的朴素方法）：
 *   1. jieba 中文分词，过滤停用词和单字；
 *   2. 统计词频，权重 = 1 + ln(词频)（词频越高权重越大，但增长放缓）；
 *   3. 用"哈希技巧"把每个词映射到 512 维中的某一维，同时用哈希值奇偶决定正负号，
 *      这样词与词即使哈希冲突到同一维，正负抵消也能降低误差；
 *   4. L2 归一化，让所有向量长度一致，余弦相似度只看方向。
 *
 * 诚实说明：这是基于"词的重合程度"的相似度，不是神经网络意义上的语义；
 * 但相比只能搜文件名已经前进了一大步，且保证了项目零依赖开箱即用。
 * 装好 Ollama 后把 provider 切成 ollama，即升级为真语义检索。
 */
@Service
@ConditionalOnProperty(name = "docmind.embedding.provider", havingValue = "tfidf", matchIfMissing = true)
public class TfidfEmbeddingService implements EmbeddingService {

    public static final int DIM = 512;

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    @Override
    public int dim() {
        return DIM;
    }

    @Override
    public List<Float> embed(String text) {
        float[] vector = new float[DIM];
        if (text == null || text.isBlank()) {
            return toList(vector);
        }
        // 1+2+3：分词 → 过滤 → 词频加权 → 哈希落维度
        Map<String, Long> termFreq = segmenter.process(text, JiebaSegmenter.SegMode.SEARCH).stream()
                .map(token -> token.word)
                .filter(word -> word.length() > 1 && !StopWords.isStopWord(word))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        termFreq.forEach((term, freq) -> {
            double weight = 1 + Math.log(freq);
            int index = Math.floorMod(term.hashCode(), DIM);
            // 哈希值奇偶决定正负号：两个词冲突在同一维时，正负可以部分抵消
            double sign = Math.floorMod(term.hashCode(), 2) == 0 ? 1 : -1;
            vector[index] += sign * weight;
        });

        // 4：L2 归一化（向量除以自己的模长，变成单位向量）
        double sumOfSquares = 0;
        for (float v : vector) {
            sumOfSquares += (double) v * v;
        }
        float norm = (float) Math.sqrt(sumOfSquares);
        if (norm > 0) {
            for (int i = 0; i < DIM; i++) {
                vector[i] /= norm;
            }
        }
        return toList(vector);
    }

    private List<Float> toList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }
}
