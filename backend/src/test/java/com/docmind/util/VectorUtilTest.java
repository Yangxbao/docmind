package com.docmind.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向量工具单元测试：序列化互逆 + 余弦相似度数学性质。
 */
class VectorUtilTest {

    @Test
    @DisplayName("float向量与BLOB字节应可无损互转")
    void bytesRoundTrip() {
        List<Float> vector = List.of(0.5f, -1.25f, 3.0f, 0.0f, 1024.5f);
        byte[] bytes = VectorUtil.toBytes(vector);
        assertEquals(vector.size() * 4, bytes.length, "每个float占4字节");

        float[] restored = VectorUtil.fromBytes(bytes);
        assertEquals(vector.size(), restored.length);
        for (int i = 0; i < vector.size(); i++) {
            assertEquals(vector.get(i), restored[i], 1e-6);
        }
    }

    @Test
    @DisplayName("同方向向量余弦为1，正交为0，反向为-1")
    void cosineBasicProperties() {
        float[] a = {1, 0, 2};
        float[] sameDirection = {2, 0, 4};
        float[] orthogonal = {0, 5, 0};
        float[] opposite = {-1, 0, -2};

        assertEquals(1.0, VectorUtil.cosine(a, sameDirection), 1e-9);
        assertEquals(0.0, VectorUtil.cosine(a, orthogonal), 1e-9);
        assertEquals(-1.0, VectorUtil.cosine(a, opposite), 1e-9);
    }

    @Test
    @DisplayName("零向量与非法输入相似度为0，不抛异常")
    void cosineEdgeCases() {
        assertEquals(0, VectorUtil.cosine(new float[]{0, 0}, new float[]{1, 1}));
        assertEquals(0, VectorUtil.cosine(null, new float[]{1, 1}));
        assertEquals(0, VectorUtil.cosine(new float[]{1, 2}, new float[]{1, 2, 3}));
    }

    @Test
    @DisplayName("越接近的向量相似度越高（检索正确性的根基）")
    void closerMeansHigherScore() {
        float[] query = {1, 2, 3};
        float[] near = {1.1f, 2.1f, 2.9f};
        float[] far = {-3, 0.5f, 8};
        assertTrue(VectorUtil.cosine(query, near) > VectorUtil.cosine(query, far));
    }
}
