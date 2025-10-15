package com.cyanrocks.ui.controller;

import com.cyanrocks.ui.dao.entity.AiEnum;
import com.cyanrocks.ui.service.CommonSettingService;
import com.cyanrocks.ui.utils.OssUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @Author wjq
 * @Date 2024/9/19 16:37
 *
 *
 *
 */
@RestController
@RequestMapping("/ai/common")
@Api(tags = {"通用接口"})
public class CommonController {

    @Autowired
    private CommonSettingService settingService;

    @Autowired
    private OssUtils ossUtils;

    @GetMapping("/enum")


    @ApiOperation(value = "获取枚举列表")
    public List<AiEnum> getEnumList(@RequestParam(value="type") String type) {
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
            public ResponseEntity<Object> downloadExcel(@RequestParam(value="objectName") String objectName, @RequestParam(value="authorization", required = false) String authorization,
                                                HttpServletRequest request, HttpServletResponse response) {
        byte[] fileContent = ossUtils.downloadFromOss(objectName);

        if (fileContent == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        HttpHeaders headers = new HttpHeaders();
        try {
            String[] objectNames = objectName.split("/");
            String fileName = objectNames[objectNames.length-1];
            response.addHeader("Content-Disposition", "attachment;filename="
                    + new String(request.getHeader("User-Agent").contains("MSIE")?fileName.getBytes():fileName.getBytes(StandardCharsets.UTF_8),"ISO8859-1"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
    }

    @GetMapping("/download-url")
    @ApiOperation(value = "获取文件url")
    public String downloadUrl(@RequestParam(value="objectName") String objectName,
                                                HttpServletRequest request, HttpServletResponse response) throws Exception {
        return ossUtils.downloadUrl(objectName);

    }

}
