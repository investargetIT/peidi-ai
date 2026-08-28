package com.cyanrocks.ai.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 企业微信订单关联请求VO
 * @author yangshihao
 */
@Data
@ApiModel(description = "企业微信订单关联请求参数")
public class QywxOrderLinkReqVO {

    @ApiModelProperty(value = "企业成员的userid", required = true)
    private String userid;

    @ApiModelProperty(value = "企业微信外部联系人ID", required = true)
    private String externalUserid;

    @ApiModelProperty(value = "订单编号", required = true)
    private String orderNo;
}
