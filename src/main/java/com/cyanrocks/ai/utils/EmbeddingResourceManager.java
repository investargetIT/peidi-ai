package com.cyanrocks.ai.utils;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.alibaba.dashscope.embeddings.*;
import com.alibaba.dashscope.exception.NoApiKeyException;

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

//    private static final String ONNX_PATH = "D:/bge-m3/bge-m3.onnx";
//    private static final String TOKENIZER_PATH = "D:/bge-m3/tokenizer.json";

//    private static final String ONNX_PATH = "/app/models/bge-m3/bge-m3.onnx";
//    private static final String TOKENIZER_PATH = "/app/models/bge-m3/tokenizer.json";

//    private static final OrtEnvironment ENV = OrtEnvironment.getEnvironment();
//    private static final OrtSession.SessionOptions OPTS = new OrtSession.SessionOptions();
//    private static OrtSession session;
//    private static HuggingFaceTokenizer tokenizer;

//    static {
//        try {
//            session = ENV.createSession(ONNX_PATH, OPTS);
//            tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(TOKENIZER_PATH));
//        } catch (Exception e) {
//            throw new RuntimeException("初始化资源失败", e);
//        }
//    }

    public static List<Float> embedText(String text) {
        try {
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model("text-embedding-v4")
//                    .apiKey("")
                    .apiKey("")
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

//    public static List<Float> embedTextOld(String text) throws OrtException {
//        float[] clsEmbedding = BgeM3Embedder.encode(ENV, session, tokenizer, text);
//        List<Float> list = new ArrayList<>(clsEmbedding.length);
//        for (float f : clsEmbedding) {
//            list.add(f);
//        }
//        return list;
//    }
//
//    // 在应用关闭时调用此方法
//    public static void shutdown() throws Exception {
//        if (session != null) session.close();
//        if (tokenizer != null) tokenizer.close();
//        if (OPTS != null) OPTS.close();
//    }
}
