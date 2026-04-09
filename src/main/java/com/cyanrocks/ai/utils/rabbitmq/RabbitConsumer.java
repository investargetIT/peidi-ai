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


    /**
     * Process a single PDF chunk: write the chunk to a temporary file, invoke the AI model to extract text/markdown,
     * persist the resulting record into the Milvus collection "pdf_markdown", acknowledge the message on success,
     * and negatively acknowledge (without requeue) on failure; the temporary file is removed in all cases.
     *
     * @param task the PDF chunk task containing the chunk bytes, batch number, original filename and request payload
     * @param deliveryTag the RabbitMQ delivery tag for the incoming message (used for manual ACK/NACK)
     * @param channel the RabbitMQ channel used to send ACK or NACK for the message
     */
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

    /**
     * Processes an AiDraw request by generating images, uploading them to OSS, and updating the AiDraw record with resulting image paths and status.
     *
     * @param draw the AiDraw record containing request parameters (UUID, URL params, size, retry settings); this record is updated with the uploaded image list, update timestamp, and status
     */
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

    /**
     * Handles a queued WeCom callback payload: extracts the user question, runs a semantic search against the PDF markdown collection,
     * and posts a markdown reply to the message's callback URL.
     *
     * <p>The posted markdown contains the rewritten question and either the semantic search result text or a fallback message.</p>
     *
     * @param rabbit raw JSON payload from the queue; expected to contain an `sMsg` object with:
     *               - `text.content` (the incoming message text),
     *               - `from.userid` (the sender id used for semantic search context),
     *               - `response_url` (the HTTP callback URL to receive the markdown reply)
     * @throws ListenerExecutionFailedException if parsing, semantic search, HTTP request execution, or response handling fails
     */
    @RabbitListener(queues = "wecom.process.queue", containerFactory = "wecomContainerFactory")
    public void processWecom(String rabbit) {
        try {
            JSONObject rabbitObject = JSONObject.parseObject(rabbit);
            JSONObject sMsgObject = JSONObject.parseObject(rabbitObject.getString("sMsg"));

            String question = sMsgObject.getJSONObject("text").getString("content");
            if (question.startsWith("@佩蒂问问")){
                question = question.replace("@佩蒂问问","").trim();
            }
            Map<String, String> searchResult = milvusUtils.semanticSearch2(question, null, "pdf_markdown"
                    , sMsgObject.getJSONObject("from").getString("userid"), "AAT孵化项目组");

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

    /**
     * Process a customer-service (KF) request consumed from the kefu queue: perform a semantic search (optionally using images referenced in the payload), then send one or more WeCom KF text messages back to the specified recipient.
     *
     * The method obtains and caches a WeCom access token in Redis as needed, may download media referenced by the payload's image list, invokes semantic search, splits overly long responses into UTF-8 byte-sized segments, and sends each segment as a separate KF message.
     *
     * @param rabbit JSON string payload containing at least the following fields: `"que"` (the question text), `"imgList"` (array of WeCom media IDs, optional), `"touser"` (recipient), and `"open_kfid"` (KF account id)
     * @throws ListenerExecutionFailedException if processing fails (e.g., token retrieval, image download, semantic search, or sending messages)
     */
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
                    , rabbitObject.getString("touser"), "AAT孵化项目组");

            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost sendPost = new HttpPost("https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg?access_token="+token);
            sendPost.setHeader("Content-Type", "application/json");
            JSONObject sendReq = new JSONObject();
            sendReq.put("touser", rabbitObject.getString("touser"));
            sendReq.put("open_kfid", rabbitObject.get("open_kfid"));
            sendReq.put("msgtype", "text");
            String result = searchResult.get("text") == null ? "实在抱歉，这个问题超出我的解答范围啦，麻烦你移步项目群咨询项目辅导员，他们会及时为你答疑的～" : searchResult.get("text");
            String content = "### 你是否在问："+searchResult.get("rewriteQuestion")+"\n"+result;
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

    /**
     * Split a string into segments whose UTF-8 encoded byte length does not exceed the specified maximum.
     *
     * Segments preserve character boundaries (no character is split across segments).
     *
     * @param text the input string to split; if null or empty an empty list is returned
     * @param maxBytes the maximum allowed UTF-8 byte length per segment (must be > 0 for meaningful results)
     * @return a list of string segments where each segment's UTF-8 byte length is ≤ maxBytes; returns an empty list if the input is null or empty
     */
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

    /**
     * Map an image MIME content type to a filename extension.
     *
     * @param contentType the MIME type of the image (e.g., "image/png"); may be null
     * @return the corresponding file extension including the leading dot (e.g., ".png"); returns ".jpg" for null or unknown types
     */
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
