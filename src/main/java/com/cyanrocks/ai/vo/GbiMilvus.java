package com.cyanrocks.ai.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * @Author wjq
 * @Date 2025/3/25 9:50
 */
@ApiModel(description = "gbi相关参数")
@Data
public class GbiMilvus {
    @ApiModelProperty(value = "id")
    private long id;

    @ApiModelProperty(value = "milvusId")
    private String milvusId;

    @ApiModelProperty(value = "表名")
    private String tableName;

    @ApiModelProperty(value = "相关字段")
    private String field;

    @ApiModelProperty(value = "参考sql")
    private String searchSql;

    @ApiModelProperty(value = "扩展字段")
    private String metedate;

    @ApiModelProperty(value = "业务逻辑解释")
    private String gbiExplain;

    @ApiModelProperty(value = "业务逻辑解释类型")
    private Boolean explainType;
}
