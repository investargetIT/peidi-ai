package com.cyanrocks.ai.controller;

import com.cyanrocks.ai.service.CaptchaService;
import com.cyanrocks.ai.vo.request.CaptchaReqVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 验证码接口
 */
@RestController
@RequestMapping("/ai/captcha")
@Api(tags = {"验证码接口"})
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    @PostMapping("/save")
    @ApiOperation(value = "保存验证码")
    public void saveCaptcha(@RequestBody CaptchaReqVO reqVO) {
        captchaService.saveCaptcha(reqVO.getKey(), reqVO.getValue());
    }

    @GetMapping("/get")
    @ApiOperation(value = "获取验证码")
    public String getCaptcha(@RequestParam String key) {
        if (!captchaService.hasCaptcha(key)) {
            return null;
        }
        return captchaService.getCaptcha(key);
    }
}
