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
    public long id;

    @ApiModelProperty(value = "milvusId")
    public String milvusId;

    @ApiModelProperty(value = "表名")
    public String tableName;

    @ApiModelProperty(value = "相关字段")
    public String field;

    @ApiModelProperty(value = "参考sql")
    public String searchSql;

    @ApiModelProperty(value = "扩展字段")
    public String metedate;

    @ApiModelProperty(value = "业务逻辑解释")
    public String gbiExplain;

    @ApiModelProperty(value = "业务逻辑解释类型")
    public Boolean explainType;
}
