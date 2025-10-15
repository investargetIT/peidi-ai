package com.cyanrocks.ui.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Author wjq
 * @Date 2025/9/5 14:27
 */
@Data
@ApiModel(value = "pdf实体参数")
public class PdfRequest {

    public String id;

    @ApiModelProperty(value = "产品名/系列名")
    public String productName;

    @ApiModelProperty(value = "报告编号/唯一文件号")
    public String reportId;

    @ApiModelProperty(value = "文档类型(枚举)")
    public String reportType;

    @ApiModelProperty(value = "文档标题")
    public String title;

    @ApiModelProperty(value = "原文件路径或 URL")
    public String source;

    @ApiModelProperty(value = "来源系统(如:CTMS、ERP、Drive)")
    public String sourceSystem;

    @ApiModelProperty(value = "zh-CN/en等")
    public String lang;

    @ApiModelProperty(value = "报告出具日期")
    public String reportDate;

    @ApiModelProperty(value = "到期日期")
    public String expireDate;

    @ApiModelProperty(value = "入库时间")
    public String ingestDate;

    @ApiModelProperty(value = "文档版本号")
    public String version;

    @ApiModelProperty(value = "文件哈希(完整性校验)")
    public String checksum;

    @ApiModelProperty(value = "访问控制(部门/角色/用户)")
    public String visibility;

    @ApiModelProperty(value = "自定义标签")
    public String tags;

    @ApiModelProperty(value = "标准/法规编号")
    public String standardRefs;

    @ApiModelProperty(value = "valid/obsolete/draft")
    public String docStatus;

    @ApiModelProperty(value = "品牌")
    public String brand;

    @ApiModelProperty(value = "SKU/料号")
    public String sku;

    @ApiModelProperty(value = "规格/净含量")
    public String spec;

    @ApiModelProperty(value = "批次/LOT")
    public Integer batchNo;

    @ApiModelProperty(value = "扩展元素")
    public String metedate;

    public String text;

    public List<Float> vector;
}
