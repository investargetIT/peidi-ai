package com.cyanrocks.ai.utils.wechat;

import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @Author wjq
 * @Date 2026/2/5 16:34
 */
public class CustomMultipartFile implements MultipartFile {

    private final byte[] content;
    private final String name;
    private final String originalFilename;
    private final String contentType;

    /**
     * Create a MultipartFile backed by in-memory bytes and associated metadata.
     *
     * @param content          the file content as a byte array; may be {@code null} to represent empty content
     * @param name             the name of the multipart form field
     * @param originalFilename the original filename provided by the client
     * @param contentType      the MIME type of the file (may be {@code null} if unknown)
     */
    public CustomMultipartFile(byte[] content, String name, String originalFilename, String contentType) {
        this.content = content;
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
    }

    /**
     * Exposes the configured multipart field identifier.
     *
     * @return the multipart field name.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Get the original filename provided by the client.
     *
     * @return the original filename, or {@code null} if not available
     */
    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    /**
     * Get the MIME content type associated with this multipart file.
     *
     * @return the MIME type (e.g., "image/png") or `null` if the content type is unknown
     */
    @Override
    public String getContentType() {
        return contentType;
    }

    /**
     * Checks whether the wrapped file has no content.
     *
     * @return `true` if the underlying byte array is `null` or has length 0, `false` otherwise.
     */
    @Override
    public boolean isEmpty() {
        return content == null || content.length == 0;
    }

    /**
     * Return the size of the underlying file content in bytes.
     *
     * @return the number of bytes in the content, or 0 if the content is null
     */
    @Override
    public long getSize() {
        return content != null ? content.length : 0;
    }

    /**
     * Return the underlying byte array containing the file content.
     *
     * @return the underlying byte array containing the file content, or {@code null} if no content is set
     */
    @Override
    public byte[] getBytes() throws IOException {
        return content;
    }

    /**
     * Creates a new InputStream that reads from the in-memory file content.
     *
     * @return an InputStream that reads from the wrapped byte array starting at its beginning
     */
    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(content);
    }

    /**
     * Indicates that transferring the in-memory content to a filesystem location is not supported.
     *
     * @param dest the destination file that would receive the content
     * @throws UnsupportedOperationException always thrown to indicate file transfer is not supported (message: "不支持文件传输")
     */
    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        throw new UnsupportedOperationException("不支持文件传输");
    }
}

