package com.docmind.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * 向量工具类：向量的序列化（存 MySQL BLOB）与相似度计算。
 */
public final class VectorUtil {

    private VectorUtil() {
    }

    /**
     * float 向量 → 字节数组（小端序），用于写入 doc_chunk.embedding (BLOB)。
     * 一个 float 占 4 字节：512 维 ≈ 2KB，1024 维 ≈ 4KB，单块向量很小，BLOB 完全够用。
     */
    public static byte[] toBytes(List<Float> vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (Float v : vector) {
            buffer.putFloat(v == null ? 0f : v);
        }
        return buffer.array();
    }

    /** 字节数组 → float[]（与 toBytes 互逆），用于检索时从 BLOB 还原向量 */
    public static float[] fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    /**
     * 余弦相似度：衡量两个向量"方向"的接近程度，取值 [-1, 1]，越大越相似。
     * 公式：cosθ = a·b / (|a| × |b|)
     * 语义检索的原理就在这里：两段文字意思越接近，其向量方向越接近，余弦值越大。
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;      // 点积 a·b
        double normA = 0;    // |a| 的平方
        double normB = 0;    // |b| 的平方
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
