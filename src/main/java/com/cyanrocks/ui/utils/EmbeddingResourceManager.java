package com.cyanrocks.ui.utils;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author wjq
 * @Date 2025/6/4 15:41
 */
public class EmbeddingResourceManager {

//    private static final String ONNX_PATH = "D:/bge-m3/bge-m3.onnx";
//    private static final String TOKENIZER_PATH = "D:/bge-m3/tokenizer.json";

    private static final String ONNX_PATH = "/app/models/bge-m3/bge-m3.onnx";
    private static final String TOKENIZER_PATH = "/app/models/bge-m3/tokenizer.json";

    private static final OrtEnvironment ENV = OrtEnvironment.getEnvironment();
    private static final OrtSession.SessionOptions OPTS = new OrtSession.SessionOptions();
    private static OrtSession session;
    private static HuggingFaceTokenizer tokenizer;

    static {
        try {
            session = ENV.createSession(ONNX_PATH, OPTS);
            tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(TOKENIZER_PATH));
        } catch (Exception e) {
            throw new RuntimeException("初始化资源失败", e);
        }
    }

    public static List<Float> embedText(String text) throws OrtException {
        float[] clsEmbedding = BgeM3Embedder.encode(ENV, session, tokenizer, text);
        List<Float> list = new ArrayList<>(clsEmbedding.length);
        for (float f : clsEmbedding) {
            list.add(f);
        }
        return list;
    }

    // 在应用关闭时调用此方法
    public static void shutdown() throws Exception {
        if (session != null) session.close();
        if (tokenizer != null) tokenizer.close();
        if (OPTS != null) OPTS.close();
    }
}