package com.cyanrocks.ai.controller;

import cn.hutool.core.util.URLUtil;
import com.cyanrocks.ai.dao.entity.AiEnum;
import com.cyanrocks.ai.service.AiChewyDetailService;
import com.cyanrocks.ai.service.CommonSettingService;
import com.cyanrocks.ai.utils.OssUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @Author wjq
 * @Date 2024/9/19 16:37
 */
@RestController
@RequestMapping("/ai/common")
@Api(tags = {"通用接口"})
public class CommonController {

    @Autowired
    private CommonSettingService settingService;

    @Autowired
    private AiChewyDetailService aiChewyDetailService;

    @Autowired
    private OssUtils ossUtils;

    @GetMapping("/enum")


    @ApiOperation(value = "获取枚举列表")
    public List<AiEnum> getEnumList(@RequestParam(value = "type") String type) {
        return settingService.getEnumList(type);
    }

    @PostMapping("/enum")
    @ApiOperation(value = "设置枚举")
    public void setEnumList(@RequestBody List<AiEnum> reqs) {
        settingService.setEnumList(reqs);
    }

    @DeleteMapping("/enum/{id}")
    @ApiOperation(value = "删除枚举")
    public void deleteEnum(@PathVariable("id") Long id) {
        settingService.deleteEnum(id);
    }

    @GetMapping("/download")
    @ApiOperation(value = "下载文件")
    public ResponseEntity<Object> downloadExcel(@RequestParam(value = "objectName") String objectName, @RequestParam(value = "authorization", required = false) String authorization,
                                                HttpServletRequest request, HttpServletResponse response) {
        byte[] fileContent = ossUtils.downloadFromOss(objectName);

        if (fileContent == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        HttpHeaders headers = new HttpHeaders();
        try {
            String[] objectNames = objectName.split("/");
            String fileName = objectNames[objectNames.length - 1];
            response.addHeader("Content-Disposition", "attachment;filename="
                    + new String(request.getHeader("User-Agent").contains("MSIE") ? fileName.getBytes() : fileName.getBytes(StandardCharsets.UTF_8), "ISO8859-1"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
    }

    @GetMapping("/gbiDownload")
    @ApiOperation(value = "下载文件")
    public ResponseEntity<byte[]> gbiDownload(
            @RequestParam("objectName") String objectName,
            HttpServletRequest request) {

        byte[] fileContent = ossUtils.downloadFromOss(URLUtil.decode(objectName));
        if (fileContent == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        // 提取文件名
        String[] parts = objectName.split("/");
        String fileName = Pattern.compile("^openai_\\d+").matcher(parts[parts.length - 1]).replaceFirst("");
        // 推荐：根据文件扩展名设置 Content-Type
        String contentType = getContentTypeByFileName(fileName);

        // 构建 HttpHeaders
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));

        // 设置 Content-Disposition：兼容现代浏览器和 iOS
        String encodedFileName = UriUtils.encode(fileName, StandardCharsets.UTF_8);
        // 使用 RFC 5987 标准：filename*="UTF-8''<encoded-name>"
        headers.setContentDispositionFormData("attachment", encodedFileName);

        // 可选：禁用缓存（避免 iOS 缓存错误响应）
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileContent);
    }

    // 辅助方法：根据文件名返回 MIME 类型
    private String getContentTypeByFileName(String fileName) {
        String ext = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0) {
            ext = fileName.substring(lastDot).toLowerCase();
        }

        switch (ext) {
            case ".pdf":
                return "application/pdf";
            case ".doc":
                return "application/msword";
            case ".docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls":
                return "application/vnd.ms-excel";
            case ".xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".txt":
                return "text/plain";
            case ".csv":
                return "text/csv";
            default:
                return "application/octet-stream";
        }
    }

    @GetMapping("/download-url")
    @ApiOperation(value = "获取文件url")
    public String downloadUrl(@RequestParam(value = "objectName") String objectName,
                              HttpServletRequest request, HttpServletResponse response) throws Exception {
        return ossUtils.downloadUrl(objectName);

    }

    @PostMapping("/chewy/parse")
    @ApiOperation(value = "文件上传")
    public void parseChewy(@RequestParam(value = "url") String url,
                             @RequestParam(value = "title") String title,
                             @RequestParam(value = "detail",required = false) MultipartFile detail,
                             @RequestParam(value = "ingredientInformation",required = false) MultipartFile ingredientInformation) {
        aiChewyDetailService.parseChewy(url, title, detail, ingredientInformation);
    }
}
