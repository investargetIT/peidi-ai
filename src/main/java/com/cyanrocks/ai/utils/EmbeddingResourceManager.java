package com.cyanrocks.ai.utils;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.alibaba.dashscope.embeddings.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author wjq
 * @Date 2025/6/4 15:41
 */
public class EmbeddingResourceManager {

    @Value("${dashscope.api-key}")
    private static String DASHSCOPE_API_KEY;

    public static List<Float> embedText(String text) {
        try {
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model("text-embedding-v4")
                    .apiKey(DASHSCOPE_API_KEY)
                    .texts(Collections.singletonList(text))
                    .build();

            TextEmbedding client = new TextEmbedding();
            TextEmbeddingResult result = client.call(param);

            TextEmbeddingOutput output = result.getOutput();
            if (output == null) {
                throw new RuntimeException("Embedding output is null");
            }

            List<TextEmbeddingResultItem> items = output.getEmbeddings();
            if (items == null || items.isEmpty()) {
                throw new RuntimeException("No embedding items in output");
            }

            List<Double> vector = items.get(0).getEmbedding();
            if (vector == null) {
                throw new RuntimeException("Embedding vector is null");
            }

            return vector.stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // 建议捕获 DashScope 的异常或检查 ErrorInfo
            throw new RuntimeException("Failed to get embedding: " + e.getMessage(), e);
        }
    }
}
