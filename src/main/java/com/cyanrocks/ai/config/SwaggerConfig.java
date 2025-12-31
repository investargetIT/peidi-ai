package com.cyanrocks.ai.config;

import com.cyanrocks.common.em.ResCodeEnum;
import io.swagger.annotations.ApiOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.builders.ResponseMessageBuilder;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.service.Parameter;
import springfox.documentation.service.ResponseMessage;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Author wjq
 * @Date 2024/7/23 17:04
 */
@Configuration
@EnableSwagger2
public class SwaggerConfig {

    @Bean
    public Docket swaggerSpringMvcPlugin() {
        List<ResponseMessage> responseMessageList = new ArrayList<>();
        responseMessageList.add(new ResponseMessageBuilder().code(ResCodeEnum.INTERNAL_ERROR.getErrCode())
                .message(ResCodeEnum.INTERNAL_ERROR.getErrMsg()).responseModel(new ModelRef(ResCodeEnum.INTERNAL_ERROR.getErrMsg())).build());
        responseMessageList.add(new ResponseMessageBuilder().code(ResCodeEnum.INVALID_CLIENT.getErrCode())
                .message(ResCodeEnum.INVALID_CLIENT.getErrMsg())
                .responseModel(new ModelRef(ResCodeEnum.INVALID_CLIENT.getErrMsg())).build());
        responseMessageList.add(new ResponseMessageBuilder().code(ResCodeEnum.SUCCESS.getErrCode())
                .message(ResCodeEnum.SUCCESS.getErrMsg()).responseModel(new ModelRef(ResCodeEnum.SUCCESS.getErrMsg())).build());

        List<Parameter> headers = new ArrayList<>();
        headers.add(new ParameterBuilder()
                .name("Authorization")
                .description("Custom header description")
                .modelRef(new ModelRef("string"))
                .parameterType("header")
                .required(false)
                .build());

        return new Docket(DocumentationType.SWAGGER_2)
                .globalOperationParameters(headers)
                // 去掉swagger默认的状态码
                .useDefaultResponseMessages(false).globalResponseMessage(RequestMethod.GET, responseMessageList)
                .globalResponseMessage(RequestMethod.POST, responseMessageList)
                .globalResponseMessage(RequestMethod.PUT, responseMessageList)
                .globalResponseMessage(RequestMethod.DELETE, responseMessageList).select()
                .apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation.class)).paths(PathSelectors.any()).build()
                .apiInfo(apiInfo());
    }

    /**
     * 这个方法主要是写一些文档的描述
     */
    private ApiInfo apiInfo() {
        return new ApiInfo("peidi-ai docker服务", "peidi-ui服务的API接口文档", "1.0", "",
                new Contact("王家琦", "", "1084948949@qq.com"), "", "", Collections.emptyList());
    }
}
