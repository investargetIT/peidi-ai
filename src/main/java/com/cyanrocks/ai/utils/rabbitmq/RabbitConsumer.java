package com.cyanrocks.ai.utils.rabbitmq;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cyanrocks.ai.dao.entity.AiDraw;
import com.cyanrocks.ai.dao.entity.AiMilvusPdfMarkdown;
import com.cyanrocks.ai.dao.mapper.AiDrawMapper;
import com.cyanrocks.ai.service.AiDrawService;
import com.cyanrocks.ai.utils.AiModelUtils;
import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.utils.OssUtils;
import com.cyanrocks.ai.utils.http.HttpClientService;
import com.cyanrocks.ai.utils.http.HttpResponseContent;
import com.cyanrocks.ai.utils.http.HttpTimeoutConfig;
import com.cyanrocks.ai.utils.http.HttpUtils;
import com.cyanrocks.ai.utils.wechat.CustomMultipartFile;
import com.cyanrocks.ai.utils.wechat.WXBizJsonMsgCrypt;
import com.rabbitmq.client.Channel;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    @Autowired
    private AiDrawService aiDrawService;
    @Autowired
    private OssUtils ossUtils;
    @Autowired
    private AiDrawMapper aiDrawMapper;
    @Autowired
    private HttpClientService httpClientService;
    @Value("${wechat.kefu.secret}")
    private String secret;
    @Value("${wechat.kefu.cropid}")
    private String cropid;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final String REDIS_KEY = "kefu:wechat:token";


    @RabbitListener(queues = "pdf.process.queue", containerFactory = "pdfContainerFactory")
    public void processPdfChunk(PdfChunkTask task, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                Channel channel) {
        Path targetPath = null;
        try {
            System.out.println("处理第 " + task.getBatchNo() + " 份");
            List<AiMilvusPdfMarkdown> pdfRecordMilvusList = new ArrayList<>();
            // 逻辑处理
            targetPath =  Files.createTempFile("chunk" + task.getBatchNo() + "_openai_", task.getOriginalFilename());
            Files.write(
                    targetPath,
                    task.getSplitPdf(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            AiMilvusPdfMarkdown pdfRequest = JSON.toJavaObject(JSON.parseObject(task.getRequestStr()), AiMilvusPdfMarkdown.class);
            String fullResponse = aiModelUtils.processFile(targetPath);
            if (fullResponse == null || fullResponse.isEmpty()) {
                throw new RuntimeException("PDF处理失败，模型返回为空");
            }
            pdfRequest.setCreateAt(LocalDateTime.now());
            pdfRequest.setText(fullResponse);
            pdfRequest.setBatchNo(task.getBatchNo());
            pdfRequest.setSource(task.getSource());
            pdfRecordMilvusList.add(pdfRequest);

            System.out.println("开始写入数据库");
            milvusUtils.processFileData(pdfRecordMilvusList, "pdf_markdown");
            System.out.println("第 " + task.getBatchNo() + " 份完成");
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            System.err.println("处理失败: " + e.getMessage());
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                System.err.println("nack 失败，消息状态未知:" + ex);
            }
        } finally {
            if (targetPath != null) {
                try {
                    Files.deleteIfExists(targetPath);
                } catch (IOException ex) {
                    System.err.println("清理临时文件失败: " + ex.getMessage());
                }
            }
        }
    }

    @RabbitListener(queues = "draw.process.queue", containerFactory = "drawContainerFactory")
    public void processDraw(AiDraw draw){
        System.out.println("开始处理 " + draw.getUuid() + " 图片");

        List<byte[]> resultList = aiDrawService.sendDrawingRequestSync(draw.getUrlParam(), draw.getUuid(),draw.getSize(),draw.getMaxRetries());
        List<String> imgs = new ArrayList<>();
        String exist = aiDrawMapper.selectOne(Wrappers.<AiDraw>lambdaQuery().eq(AiDraw::getUuid,draw.getUuid())).getImgs();
        if (null != exist){
            imgs.addAll(JSONArray.parseArray(exist,String.class));
        }
        resultList.forEach(result->{
            String objectName = "ai/draw/grsai/"+ UUID.randomUUID() +".png";
            ossUtils.uploadToOss(objectName, result);
            imgs.add(objectName);
        });
        draw.setImgs(JSONObject.toJSONString(imgs));
        draw.setUpdateAt(LocalDateTime.now());
        if (CollectionUtil.isNotEmpty(resultList)){
            draw.setStatus(1);//成功
        }else {
            draw.setStatus(2);//失败
        }
        aiDrawService.update(draw,Wrappers.<AiDraw>lambdaQuery().eq(AiDraw::getUuid,draw.getUuid()));
        System.out.println("处理完成 " + draw.getUuid() + " 图片");
    }

    // wecom 消息已改为 controller 直接异步处理，不再使用 RabbitMQ
    // @RabbitListener(queues = "wecom.process.queue", containerFactory = "wecomContainerFactory")
    public void processWecom(String rabbit) {
        try {
            JSONObject rabbitObject = JSONObject.parseObject(rabbit);
            JSONObject sMsgObject = JSONObject.parseObject(rabbitObject.getString("sMsg"));

            String question = sMsgObject.getJSONObject("text").getString("content");
            if (question.startsWith("@佩蒂问问")){
                question = question.replace("@佩蒂问问","").trim();
            }
            Map<String, String> searchResult = milvusUtils.semanticSearch2(question, null, "pdf_markdown"
                    , sMsgObject.getJSONObject("from").getString("userid"), "AAT孵化项目组","callWithMessageWithImg");

            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(sMsgObject.getString("response_url").trim());
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject requestBody = new JSONObject();
            requestBody.put("msgtype", "markdown");
            JSONObject markdown = new JSONObject();
            String result = searchResult.get("text") == null ? "请稍后再试" : searchResult.get("text");
            markdown.put("content","### 你是否在问："+searchResult.get("rewriteQuestion")+"\n"+result);
            markdown.put("feedback", new JSONObject().fluentPut("id", searchResult.get("id")));
            requestBody.put("markdown",markdown);

            httpPost.setEntity(new StringEntity(
                    JSONObject.toJSONString(requestBody),
                    ContentType.APPLICATION_JSON
            ));
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                System.out.println("WeChat Response: " + responseBody);

                int status = response.getStatusLine().getStatusCode();
                if (status == 200) {
                    System.out.println("发送成功");
                } else {
                    System.err.println("发送失败，状态码: " + status);
                }
            }
        }catch (Exception e) {
            System.err.println("处理失败: " + JSONObject.toJSONString(rabbit));
            System.err.println("处理失败: " + e.getMessage());
            throw new ListenerExecutionFailedException("处理失败", e);
        }
    }

    @RabbitListener(queues = "kefu.process.queue", containerFactory = "kefuContainerFactory")
    public void processKefu(String rabbit) {
        try {
            //获取access_token
            String token = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (null == token){
                String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="+ cropid +"&corpsecret="+ secret;
                HttpResponseContent content;
                try {
                    content = httpClientService.doGet(url, null, null,
                            HttpUtils.initHttpClientContext(null, new HttpTimeoutConfig(300000)));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    return;
                }
                cn.hutool.json.JSONObject contentJson = JSONUtil.parseObj(content.getContent());
                token = contentJson.getStr("access_token");
                stringRedisTemplate.opsForValue().set(REDIS_KEY, token);
                stringRedisTemplate.expire(REDIS_KEY, Duration.ofSeconds(7200));
            }


            JSONObject rabbitObject = JSONObject.parseObject(rabbit);

            String question = rabbitObject.getString("que");

            List<String> imgList = JSONObject.parseArray(rabbitObject.getString("imgList"),String.class);
            List<MultipartFile> files = new ArrayList<>();
            if (CollectionUtil.isNotEmpty(imgList)){
                //下载图片
                for (String mediaId : imgList) {
                    String url = "https://qyapi.weixin.qq.com/cgi-bin/media/get?access_token=" + token + "&media_id=" + mediaId;
                    System.out.println("下载图片："+url);
                    try {
                        // 1. 下载图片字节（doGetBytes 应返回 byte[]）
                        byte[] imageBytes = httpClientService.doGetBytes(url, null);
                        if (imageBytes == null || imageBytes.length == 0) {
                            System.out.println("Empty image data for URL: " + url);
                            continue;
                        }

                        // 2. 确定 Content-Type（根据 mediaId 或默认值）
                        String contentType = "image/jpeg"; // 默认
                        if (mediaId != null && (mediaId.contains(".png") || mediaId.endsWith("png"))) {
                            contentType = "image/png";
                        }
                        // 可扩展其他格式
                        else if (mediaId != null && (mediaId.contains(".gif") || mediaId.endsWith("gif"))) {
                            contentType = "image/gif";
                        }

                        // 3. 生成文件名
                        String filename = mediaId + getFileExtension(contentType);

                        // 4. 创建自定义 MultipartFile
                        CustomMultipartFile multipartFile = new CustomMultipartFile(
                                imageBytes,
                                "file",          // 表单字段名（通常为 "file"）
                                filename,
                                contentType
                        );
                        files.add(multipartFile);

                    } catch (Exception e) {
                        System.err.println("下载图片失败, URL: " + url + ", Error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            System.out.println("下载成功"+files.size()+"张图片");
            Map<String, String> searchResult = milvusUtils.semanticSearch3(question, files, "pdf_markdown"
                    , rabbitObject.getString("touser"), "AAT孵化项目组","callWithMessageWithImg");

            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost sendPost = new HttpPost("https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg?access_token="+token);
            sendPost.setHeader("Content-Type", "application/json");
            JSONObject sendReq = new JSONObject();
            sendReq.put("touser", rabbitObject.getString("touser"));
            sendReq.put("open_kfid", rabbitObject.get("open_kfid"));
            sendReq.put("msgtype", "text");
            String result = searchResult.get("text") == null ? "本助手仅支持群聊场景使用" : searchResult.get("text");
            String content = stripMarkdown(result);
            // 检查是否超长（2048 字节）
            List<String> contentList = new ArrayList<>();
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            System.out.println("content length: " + contentBytes.length);
            if (contentBytes.length <= 2000) {
                contentList.add(content);
            } else {
                // 超长，分片（每片最多 2000 字节）
                contentList = splitByByteLength(content, 2000);
            }
            if (CollectionUtil.isNotEmpty(contentList)){
                for (String sendContent: contentList){
                    sendReq.put("msgid", UUID.randomUUID().toString().replace("-", ""));
                    sendReq.put("text", new JSONObject().fluentPut("content", sendContent));
                    sendPost.setEntity(new StringEntity(
                            JSONObject.toJSONString(sendReq),
                            ContentType.APPLICATION_JSON
                    ));
                    try (CloseableHttpResponse response = httpClient.execute(sendPost)) {
                        String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                        System.out.println("WeChat Response: " + responseBody);

                        int status = response.getStatusLine().getStatusCode();
                        if (status == 200) {
                            System.out.println("发送成功");
                        } else {
                            System.err.println("发送失败，状态码: " + status);
                        }
                    }
                }
            }
        }catch (Exception e) {
            System.err.println("处理失败: " + JSONObject.toJSONString(rabbit));
            System.err.println("处理失败: " + e.getMessage());
            throw new ListenerExecutionFailedException("处理失败", e);
        }
    }

    private String stripMarkdown(String text) {
        if (text == null) return null;
        String[] lines = text.replaceAll("<br\\s*/?>", "\n").split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            // 跳过表格分隔行
            if (trimmed.matches("^[|:\\- ]+$")) continue;
            // 表格行：去掉 | 分隔符，用空格连接各列
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                String row = trimmed.substring(1, trimmed.length() - 1);
                String cols = java.util.Arrays.stream(row.split("\\|"))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.joining("  "));
                if (!cols.isEmpty()) sb.append(cols).append("\n");
                continue;
            }
            // 标题：去掉 #
            trimmed = trimmed.replaceAll("^#{1,6}\\s*", "");
            // 粗体/斜体
            trimmed = trimmed.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("\\*(.+?)\\*", "$1");
            // 引用
            trimmed = trimmed.replaceAll("^>\\s*", "");
            // 列表
            trimmed = trimmed.replaceAll("^[*\\-]\\s+", "• ");
            sb.append(trimmed).append("\n");
        }
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private List<String> splitByByteLength(String text, int maxBytes) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;

        for (char c : text.toCharArray()) {
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;

            if (currentBytes + charBytes > maxBytes && current.length() > 0) {
                parts.add(current.toString());
                current = new StringBuilder();
                currentBytes = 0;
            }

            current.append(c);
            currentBytes += charBytes;
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts;
    }

    private String getFileExtension(String contentType) {
        if (contentType == null) return ".jpg";

        switch (contentType.toLowerCase()) {
            case "image/jpeg":
            case "image/jpg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/gif":
                return ".gif";
            case "image/bmp":
                return ".bmp";
            case "image/webp":
                return ".webp";
            default:
                return ".jpg";
        }
    }
}
