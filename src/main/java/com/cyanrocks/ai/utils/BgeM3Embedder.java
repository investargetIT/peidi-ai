package com.cyanrocks.ai.utils;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author wjq
 * @Date 2025/4/2 10:43
 */
@Component
public class BgeM3Embedder {
    private static String poolingMethod = "cls";
    private static boolean normalizeEmbeddings = true;

    public static void main(String[] args) throws OrtException, IOException {
        // 加载ONNX模型
        String modelPath = "D:/bge-m3/bge-m3.onnx";// "bge-m3.onnx";
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        OrtSession session = env.createSession(modelPath, opts);

        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(Paths.get("D:/bge-m3/tokenizer.json"));

        String[] texts = {
                "This is a sample text for embedding calculation.",
                "Another example text to test the model.",
                "Yet another text to ensure consistency.",
                "Testing with different lengths and contents.",
                "Final text to verify the ONNX model accuracy.",
                "中文数据测试",
                "随便说点什么吧！反正也只是测试用！",
                "你好！jone！"
        };

        for (String text : texts) {
            float[] clsEmbedding = encode(env, session, tokenizer, text);
            System.out.println(Arrays.toString(clsEmbedding));
        }
        session.close();
        env.close();
    }

    private static long[] padArray(long[] array, int length) {
        long[] paddedArray = new long[length];
        System.arraycopy(array, 0, paddedArray, 0, Math.min(array.length, length));
        return paddedArray;
    }

    public static float[] encode(OrtEnvironment env, OrtSession session, HuggingFaceTokenizer tokenizer, String text) throws OrtException {
        Encoding enc = tokenizer.encode(text);
        long[] inputIdsData = enc.getIds();
        long[] attentionMaskData = enc.getAttentionMask();


        int maxLength = 128;
        int batchSize = 1;

        long[] inputIdsShape = new long[]{batchSize, maxLength};
        long[] attentionMaskShape = new long[]{batchSize, maxLength};
        // 确保数组长度为128
        inputIdsData = padArray(inputIdsData, maxLength);
        attentionMaskData = padArray(attentionMaskData, maxLength);

        OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIdsData), inputIdsShape);
        OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMaskData), attentionMaskShape);

        // 创建输入的Map
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", inputIdsTensor);
        inputs.put("attention_mask", attentionMaskTensor);

        // 运行推理
        OrtSession.Result result = session.run(inputs);

        // 提取三维数组
        float[][][] lastHiddenState = (float[][][]) result.get(0).getValue();

        float[] embeddings;
        if ("cls".equals(poolingMethod)) {
            embeddings = lastHiddenState[0][0];
        } else if ("mean".equals(poolingMethod)) {
            int sequenceLength = lastHiddenState[0].length;
            int hiddenSize = lastHiddenState[0][0].length;
            float[] sum = new float[hiddenSize];
            int count = 0;

            for (int i = 0; i < sequenceLength; i++) {
                if (attentionMaskData[i] == 1) {
                    for (int j = 0; j < hiddenSize; j++) {
                        sum[j] += lastHiddenState[0][i][j];
                    }
                    count++;
                }
            }

            float[] mean = new float[hiddenSize];
            for (int j = 0; j < hiddenSize; j++) {
                mean[j] = sum[j] / count;
            }
            embeddings = mean;
        } else {
            throw new IllegalArgumentException("Unsupported pooling method: " + poolingMethod);
        }

        if (normalizeEmbeddings) {
            float norm = 0;
            for (float v : embeddings) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);
            for (int i = 0; i < embeddings.length; i++) {
                embeddings[i] /= norm;
            }
        }

        // 释放资源
        inputIdsTensor.close();
        attentionMaskTensor.close();

        return embeddings;
    }
}