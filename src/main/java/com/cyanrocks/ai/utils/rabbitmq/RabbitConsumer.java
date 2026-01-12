package com.cyanrocks.ai.utils.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.cyanrocks.ai.dao.entity.AiMilvusPdfMarkdown;
import com.cyanrocks.ai.dao.mapper.AiMilvusPdfMarkdownMapper;
import com.cyanrocks.ai.utils.AiModelUtils;
import com.cyanrocks.ai.utils.MilvusUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author wjq
 * @Date 2026/1/7 15:14
 */
@Component
public class RabbitConsumer {

    @Autowired
    private AiModelUtils aiModelUtils;
    @Autowired
    private MilvusUtils milvusUtils;

    @RabbitListener(queues = "pdf.process.queue", containerFactory = "pdfContainerFactory")
    public void processPdfChunk(PdfChunkTask task) {
        try {
            System.out.println("处理第 " + task.getBatchNo() + " 份");
            List<AiMilvusPdfMarkdown> pdfRecordMilvusList = new ArrayList<>();
            // 逻辑处理
            Path targetPath =  Files.createTempFile("chunk" + task.getBatchNo() + "_openai_", task.getOriginalFilename());
            Files.write(
                    targetPath,
                    task.getSplitPdf(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            AiMilvusPdfMarkdown pdfRequest = JSON.toJavaObject(JSON.parseObject(task.getRequestStr()), AiMilvusPdfMarkdown.class);
            String fullResponse = aiModelUtils.processFile(targetPath);
            pdfRequest.setCreateAt(LocalDateTime.now());
            pdfRequest.setText(fullResponse);
            pdfRequest.setBatchNo(task.getBatchNo());
            pdfRequest.setSource(task.getSource());
            pdfRecordMilvusList.add(pdfRequest);
            Files.deleteIfExists(targetPath);

            System.out.println("开始写入数据库");
            milvusUtils.processFileData(pdfRecordMilvusList, "pdf_markdown");
            System.out.println("第 " + task.getBatchNo() + " 份完成");

        } catch (Exception e) {
            System.err.println("处理失败: " + e.getMessage());
            throw new ListenerExecutionFailedException("处理失败", e);
        }
    }

}
