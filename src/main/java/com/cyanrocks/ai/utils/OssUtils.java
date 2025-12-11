package com.cyanrocks.ai.utils;

import com.aliyun.oss.*;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.*;
import com.cyanrocks.ai.config.OssConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @Author wjq
 * @Date 2024/8/7 10:55
 */
@Component
public class OssUtils {

    private static final Logger logger = LoggerFactory.getLogger(OssUtils.class);

    @Autowired
    private OssConfig ossConfig;

    /**
     * 分片上传，如果中途失败，在OSS会产生碎片。不会产生脏的文件数据。OSS的碎片可以在OSS端设置过期策略。
     *
     * 只有所有的碎片上传完成后，才会合成一个新的文件（目前不考虑断点续传）
     *
     * @param ossPath
     * @param localPath
     * @param partSize
     */
    public void uploadLocalFileWithMultipart(String ossPath, String localPath, Long partSize) {
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(ossConfig.getEndpoint(), ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret());
        // 创建InitiateMultipartUploadRequest对象。
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(ossConfig.getBucketName(), ossPath);
        // 初始化分片。
        InitiateMultipartUploadResult upresult = ossClient.initiateMultipartUpload(request);
        // 返回uploadId，它是分片上传事件的唯一标识。您可以根据该uploadId发起相关的操作，例如取消分片上传、查询分片上传等。
        String uploadId = upresult.getUploadId();
        // partETags是PartETag的集合。PartETag由分片的ETag和分片号组成。
        List<PartETag> partETags = new ArrayList<PartETag>();
        // 每个分片的大小，用于计算文件有多少个分片。单位为字节。
        if (null == partSize || 0 == partSize) {
            partSize = 1 * 1024 * 1024L;// 1 MB。
        }
        // 填写本地文件的完整路径。如果未指定本地路径，则默认从示例程序所属项目对应本地路径中上传文件。
        final File sampleFile = new File(localPath);
        long fileLength = sampleFile.length();
        int partCount = (int)(fileLength / partSize);
        if (fileLength % partSize != 0) {
            partCount++;
        }
        // 遍历分片上传。
        try {
            for (int i = 0; i < partCount; i++) {
                long startPos = i * partSize;
                long curPartSize = (i + 1 == partCount) ? (fileLength - startPos) : partSize;
                InputStream instream = new FileInputStream(sampleFile);
                // 跳过已经上传的分片。
                instream.skip(startPos);
                UploadPartRequest uploadPartRequest = new UploadPartRequest();
                uploadPartRequest.setBucketName(ossConfig.getBucketName());
                uploadPartRequest.setKey(ossPath);
                uploadPartRequest.setUploadId(uploadId);
                uploadPartRequest.setInputStream(instream);
                // 设置分片大小。除了最后一个分片没有大小限制，其他的分片最小为100 KB。
                uploadPartRequest.setPartSize(curPartSize);
                // 设置分片号。每一个上传的分片都有一个分片号，取值范围是1~10000，如果超出此范围，OSS将返回InvalidArgument错误码。
                uploadPartRequest.setPartNumber(i + 1);
                // 每个分片不需要按顺序上传，甚至可以在不同客户端上传，OSS会按照分片号排序组成完整的文件。
                UploadPartResult uploadPartResult = ossClient.uploadPart(uploadPartRequest);
                // 每次上传分片之后，OSS的返回结果包含PartETag。PartETag将被保存在partETags中。
                partETags.add(uploadPartResult.getPartETag());
            }
        } catch (Exception e) {
            logger.error("Multipart Upload error");
        }
        // 创建CompleteMultipartUploadRequest对象。
        // 在执行完成分片上传操作时，需要提供所有有效的partETags。OSS收到提交的partETags后，会逐一验证每个分片的有效性。当所有的数据分片验证通过后，OSS将把这些分片组合成一个完整的文件。
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(ossConfig.getBucketName(), ossPath, uploadId, partETags);
        // 完成上传。
        CompleteMultipartUploadResult completeMultipartUploadResult =
                ossClient.completeMultipartUpload(completeMultipartUploadRequest);
        System.out.println(completeMultipartUploadResult.getETag());
        // 关闭OSSClient。
        ossClient.shutdown();
    }

    public void downloadOssFileWithMultipart(String ossPath, String localPath, Long partSize) throws Throwable {
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(ossConfig.getEndpoint(), ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret());
        if (null == partSize || 0 == partSize) {
            partSize = 1 * 1024 * 1024L;// 1 MB。
        }
        // 下载请求，10个任务并发下载，启动断点续传。
        DownloadFileRequest downloadFileRequest = new DownloadFileRequest(ossConfig.getBucketName(), ossPath);
        // localPath:D:\\localpath\\examplefile.txt
        downloadFileRequest.setDownloadFile(localPath);
        downloadFileRequest.setPartSize(partSize);
        downloadFileRequest.setTaskNum(10);
        downloadFileRequest.setEnableCheckpoint(true);
        downloadFileRequest.setCheckpointFile(localPath + ".dcp");
        // 下载文件。
        ossClient.downloadFile(downloadFileRequest);
        // 关闭OSSClient。
        ossClient.shutdown();
    }

    public void uploadPartCopyFile(String sourceKey, String destinationKey, Long partSize) {
        // 创建OSSClient实例。
        String sourceBucketName = ossConfig.getBucketName();
        String destinationBucketName = ossConfig.getBucketName();
        OSS ossClient = new OSSClientBuilder().build(ossConfig.getEndpoint(), ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret());
        if (null == partSize || 0 == partSize) {
            partSize = 1 * 1024 * 1024L;// 1 MB。
        }
        ObjectMetadata objectMetadata = ossClient.getObjectMetadata(sourceBucketName, sourceKey);
        // 获取被拷贝文件的大小。
        long contentLength = objectMetadata.getContentLength();
        // 计算分片总数。
        int partCount = (int)(contentLength / partSize);
        if (contentLength % partSize != 0) {
            partCount++;
        }
        System.out.println("total part count:" + partCount);

        // 初始化拷贝任务。可以通过InitiateMultipartUploadRequest指定目标文件元信息。
        InitiateMultipartUploadRequest initiateMultipartUploadRequest =
                new InitiateMultipartUploadRequest(destinationBucketName, destinationKey);
        InitiateMultipartUploadResult initiateMultipartUploadResult =
                ossClient.initiateMultipartUpload(initiateMultipartUploadRequest);
        String uploadId = initiateMultipartUploadResult.getUploadId();

        // 分片拷贝。
        List<PartETag> partETags = new ArrayList<PartETag>();
        for (int i = 0; i < partCount; i++) {
            // 计算每个分片的大小。
            long skipBytes = partSize * i;
            long size = partSize < contentLength - skipBytes ? partSize : contentLength - skipBytes;

            // 创建UploadPartCopyRequest。可以通过UploadPartCopyRequest指定限定条件。
            UploadPartCopyRequest uploadPartCopyRequest =
                    new UploadPartCopyRequest(sourceBucketName, sourceKey, destinationBucketName, destinationKey);
            uploadPartCopyRequest.setUploadId(uploadId);
            uploadPartCopyRequest.setPartSize(size);
            uploadPartCopyRequest.setBeginIndex(skipBytes);
            uploadPartCopyRequest.setPartNumber(i + 1);
            // Map headers = new HashMap();
            // 指定拷贝的源地址。
            // headers.put(OSSHeaders.COPY_OBJECT_SOURCE, "/examplebucket/desexampleobject.txt");
            // 指定源Object的拷贝范围。例如设置bytes=0~1023，表示拷贝1~1024字节的内容。
            // headers.put(OSSHeaders.COPY_SOURCE_RANGE, "bytes=0~1023");
            // 如果源Object的ETag值和您提供的ETag相等，则执行拷贝操作，并返回200 OK。
            // headers.put(OSSHeaders.COPY_OBJECT_SOURCE_IF_MATCH, "5B3C1A2E053D763E1B002CC607C5****");
            // 如果源Object的ETag值和您提供的ETag不相等，则执行拷贝操作，并返回200 OK。
            // headers.put(OSSHeaders.COPY_OBJECT_SOURCE_IF_NONE_MATCH, "5B3C1A2E053D763E1B002CC607C5****");
            // 如果指定的时间等于或者晚于文件实际修改时间，则正常拷贝文件，并返回200 OK。
            // headers.put(OSSHeaders.COPY_OBJECT_SOURCE_IF_UNMODIFIED_SINCE, "2021-12-09T07:01:56.000Z");
            // 如果源Object在用户指定的时间以后被修改过，则执行拷贝操作。
            // headers.put(OSSHeaders.COPY_OBJECT_SOURCE_IF_MODIFIED_SINCE, "2021-12-09T07:01:56.000Z");
            // uploadPartCopyRequest.setHeaders(headers);
            UploadPartCopyResult uploadPartCopyResult = ossClient.uploadPartCopy(uploadPartCopyRequest);

            // 将返回的分片ETag保存到partETags中。
            partETags.add(uploadPartCopyResult.getPartETag());
        }

        // 提交分片拷贝任务。
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(destinationBucketName, destinationKey, uploadId, partETags);
        ossClient.completeMultipartUpload(completeMultipartUploadRequest);

        // 关闭OSSClient。
        ossClient.shutdown();
    }

    public void uploadToOss(String objectName, byte[] content) {
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(ossConfig.getEndpoint(), ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret());
        try {
            // 创建上传请求
            PutObjectRequest putObjectRequest = new PutObjectRequest(ossConfig.getBucketName(), objectName, new ByteArrayInputStream(content), null);
            // 上传文件
            ossClient.putObject(putObjectRequest);
        } finally {
            // 关闭 OSS 客户端

            ossClient.shutdown();
        }
    }

    public byte[] downloadFromOss(String objectKey) {
        OSS ossClient = new OSSClientBuilder().build(ossConfig.getEndpoint(), ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret());
        byte[] fileContent = null;

        try {
            // 获取对象
            OSSObject ossObject = ossClient.getObject(ossConfig.getBucketName(), objectKey);
            InputStream inputStream = ossObject.getObjectContent();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // 将输入流写入输出流
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }

            // 获取文件内容
            fileContent = outputStream.toByteArray();

            // 关闭输入流
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭 OSSClient
            ossClient.shutdown();
        }

        return fileContent;
    }

    public String downloadUrl(String objectKey) throws Exception {
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        CredentialsProvider credentialsProvider = new DefaultCredentialProvider(ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret());

        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        OSS ossClient = OSSClientBuilder.create()
                .endpoint(ossConfig.getEndpoint())
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(clientBuilderConfiguration)
                .region("cn-hangzhou")
                .build();
        try {
            Date expiration = new Date(new Date().getTime() + 7*24*3600 * 1000L);
            URL url = ossClient.generatePresignedUrl(ossConfig.getBucketName(), objectKey, expiration);
            return url.toString();
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
        return null;
    }
}

