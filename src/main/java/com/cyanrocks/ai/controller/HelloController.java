package com.cyanrocks.ai.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(tags = {"测试接口"})
public class HelloController {

    @GetMapping("/")
    @ApiOperation(value = "测试")
    public String index() {
        return "Greetings from Spring Boot!";
    }

}