package com.cyanrocks.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiGbiExplain;
import com.cyanrocks.ai.dao.entity.AiGbiTable;
import com.cyanrocks.ai.log.Log;
import com.cyanrocks.ai.service.GbiService;
import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.vo.GbiMilvus;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * @Author wjq
 * @Date 2025/11/18 12:00
 */
@RestController
@RequestMapping("/ai/gbi")
@Api(tags = {"Gbi相关接口"})
public class GbiController {

    @Autowired
    private MilvusUtils milvusUtils;

    @Autowired
    private GbiService gbiService;

    private static final String gbiTableMilvusCollection = "gbi_table";
    private static final String gbiExplainMilvusCollection = "gbi_explain";

    @GetMapping("/question")
    @ApiOperation(value = "查询问题")
    public Map<String, String> gbiQuestion(@RequestParam String question) throws Exception {
        return milvusUtils.gbiSearch(question, gbiTableMilvusCollection, gbiExplainMilvusCollection,"17210074020113233");
    }

    @PostMapping("/table/new")
    @ApiOperation(value = "新增gbi表")
    public void newGbiTable(@RequestBody GbiMilvus gbiMilvus) {
        milvusUtils.processGbiTable(gbiMilvus, gbiTableMilvusCollection);
    }

    @GetMapping("/table/page")
    @ApiOperation(value = "分页gbi表")
    public IPage<AiGbiTable> getGbiTablePage(@RequestParam int pageNo, @RequestParam int pageSize,
                                             @RequestParam(value = "sortStr", required = false) String sortStr,
                                             @RequestParam(value = "searchStr", required = false) String searchStr) {
        return gbiService.getGbiTablePage(pageNo, pageSize, sortStr, searchStr);
    }

    @PostMapping("/table/update")
    @ApiOperation(value = "修改gbi表")
    @Log(value = "Gbi相关接口-修改gbi表")
    public void updateGbiTable(@RequestBody AiGbiTable aiGbiTable) {
        aiGbiTable.setUpdateAt(LocalDateTime.now());
        gbiService.updateGbiTable(aiGbiTable, gbiTableMilvusCollection);
    }

    @PostMapping("/table/delete")
    @ApiOperation(value = "删除gbi表")
    @Log(value = "Gbi相关接口-删除gbi表")
    public void deleteGbiTable(@RequestBody AiGbiTable aiGbiTable) {
        gbiService.deleteGbiTable(aiGbiTable, gbiTableMilvusCollection);
    }

    @PostMapping("/explain/new")
    @ApiOperation(value = "新增业务逻辑解释")
    public void newExplain(@RequestBody GbiMilvus gbiMilvus) {
        milvusUtils.processGbiExplain(gbiMilvus, gbiExplainMilvusCollection);
    }

    @GetMapping("/explain/page")
    @ApiOperation(value = "分页业务逻辑解释")
    public IPage<AiGbiExplain> getGbiExplainPage(@RequestParam int pageNo, @RequestParam int pageSize,
                                                 @RequestParam(value = "sortStr", required = false) String sortStr,
                                                 @RequestParam(value = "searchStr", required = false) String searchStr) {
        return gbiService.getGbiExplainPage(pageNo, pageSize, sortStr, searchStr);
    }

    @PostMapping("/explain/update")
    @ApiOperation(value = "修改业务逻辑解释")
    @Log(value = "Gbi相关接口-修改业务逻辑解释")
    public void updateGbiExplain(@RequestBody AiGbiExplain aiGbiExplain) {
        aiGbiExplain.setUpdateAt(LocalDateTime.now());
        gbiService.updateGbiExplain(aiGbiExplain, gbiExplainMilvusCollection);
    }

    @PostMapping("/explain/delete")
    @ApiOperation(value = "删除业务逻辑解释")
    @Log(value = "Gbi相关接口-删除业务逻辑解释")
    public void deleteGbiExplain(@RequestBody AiGbiExplain aiGbiExplain) {
        gbiService.deleteGbiExplain(aiGbiExplain, gbiExplainMilvusCollection);
    }

}
