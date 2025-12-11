package com.cyanrocks.ai.vo.request;

import io.swagger.annotations.ApiModel;
import lombok.Data;

import java.util.List;

/**
 * @Author wjq
 * @Date 2025/3/25 9:31
 */
@Data
@ApiModel(value = "久谦报告实体参数")
public class BiJiuqianReportReq {
    public String month;
    public String title;
    public String content;
    public List<String> description;
}
