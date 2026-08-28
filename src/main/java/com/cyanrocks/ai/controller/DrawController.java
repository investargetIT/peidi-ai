package com.cyanrocks.ai.controller;

import com.aliyun.core.utils.StringUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiDraw;
import com.cyanrocks.ai.dao.entity.AiDrawMaterials;
import com.cyanrocks.ai.dao.entity.AiDrawRecord;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.service.AiDrawService;
import com.cyanrocks.ai.utils.OssUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * @Author wjq
 * @Date 2026/1/12 13:26
 */
@RestController
@RequestMapping("/ai/draw")
@Api(tags = {"ai画图相关接口"})
public class DrawController {

    @Autowired
    private AiDrawService aiDrawService;
    @Autowired
    private OssUtils ossUtils;

    @PostMapping("/new")
    @ApiOperation(value = "新增画图")
    public void newDraw(@RequestBody AiDraw aiDraw) {
        aiDrawService.newDrawAsy(aiDraw);
    }

    @GetMapping("/page")
    @ApiOperation(value = "分页结果")
    public IPage<AiDraw> getPage(@RequestParam int pageNo, @RequestParam int pageSize,
                                             @RequestParam(value = "sortStr", required = false) String sortStr,
                                             @RequestParam(value = "searchStr", required = false) String searchStr) {
        return aiDrawService.getPage(pageNo, pageSize, sortStr, searchStr);
    }

    @PostMapping("/upload")
    @ApiOperation(value = "原图上传")
    public String uploadOriginImg(@RequestBody MultipartFile file) throws IOException {
        String objectName = "ai/draw/origin/"+file.getOriginalFilename();
        ossUtils.uploadToOss(objectName, file.getBytes());
        return objectName;
    }

    @PostMapping("/record/new")
    @ApiOperation(value = "图片上传历史")
    public void newRecord(@RequestBody AiDrawRecord aiDrawRecord) {
        aiDrawService.newRecord(aiDrawRecord);
    }

    @GetMapping("/record/page")
    @ApiOperation(value = "分页图片上传历史结果")
    public IPage<AiDrawRecord> getRecordPage(@RequestParam int pageNo, @RequestParam int pageSize,
                                 @RequestParam(value = "sortStr", required = false) String sortStr,
                                 @RequestParam(value = "searchStr", required = false) String searchStr) {
        return aiDrawService.getRecordPage(pageNo, pageSize, sortStr, searchStr);
    }


    @PostMapping("/materials/new")
    @ApiOperation(value = "新增素材")
    public void newMaterials(@RequestBody AiDrawMaterials AiDrawMaterials) {
        aiDrawService.newMaterials(AiDrawMaterials);
    }

    @PostMapping("/materials/update")
    @ApiOperation(value = "修改素材")
    public void updateMaterials(@RequestBody AiDrawMaterials AiDrawMaterials) {
        aiDrawService.updateMaterials(AiDrawMaterials);
    }

    @PostMapping("/materials/delete")
    @ApiOperation(value = "删除素材")
    public void deleteMaterials(@RequestBody AiDrawMaterials AiDrawMaterials) {
        aiDrawService.deleteMaterials(AiDrawMaterials);
    }

    @GetMapping("/materials/page")
    @ApiOperation(value = "分页素材库结果")
    public IPage<AiDrawMaterials> getMaterialsPage(@RequestParam int pageNo, @RequestParam int pageSize,
                                 @RequestParam(value = "sortStr", required = false) String sortStr,
                                 @RequestParam(value = "searchStr", required = false) String searchStr) {
        return aiDrawService.getMaterialsPage(pageNo, pageSize, sortStr, searchStr);
    }

    @PostMapping("/transfer")
    @ApiOperation(value = "中转")
    public List<String> transfer(@RequestBody AiDraw aiDraw) throws IOException {
        return aiDrawService.fetchImageUrls(aiDraw.getUrlParam(),null);
    }

    @PostMapping("/transfer/gemini")
    @ApiOperation(value = "中转gemini模型")
    public String transferGemini(@RequestBody AiDraw aiDraw) throws IOException {
        return aiDrawService.transferGemini(aiDraw.getUrlParam());
    }


    @PostMapping("/transfer/aliyun")
    @ApiOperation(value = "中转阿里云百炼wan2.7-image模型")
    public String transferAliyun(@RequestBody AiDraw aiDraw) {
        return aiDrawService.transferAliyun( aiDraw);
    }

    @PostMapping("/transfer/coding/plan")
    @ApiOperation(value = "中转到codingPlan")
    public String codingPlan(@RequestBody AiDraw aiDraw) {
    //todo 1.

        return aiDrawService.transferCodingPlan( aiDraw);
    }

    @PostMapping("/transfer/qnaigc")
    @ApiOperation(value = "中转qnaigc模型")
    public String transferQnaigc(@RequestBody AiDraw aiDraw) {
        // 手动检查
        if (StringUtils.isBlank(aiDraw.getUrlParam())) {
            throw new BusinessException(400, "urlParam 不能为空");
        }
        return aiDrawService.transferQnaigc(aiDraw);

    }
}
