package com.cyanrocks.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiDraw;
import com.cyanrocks.ai.dao.entity.AiDrawMaterials;
import com.cyanrocks.ai.dao.entity.AiDrawRecord;
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

    /**
     * Creates a new AI drawing task.
     *
     * @param aiDraw the drawing request containing parameters and metadata for the new task
     */
    @PostMapping("/new")
    @ApiOperation(value = "新增画图")
    public void newDraw(@RequestBody AiDraw aiDraw) {
        aiDrawService.newDrawAsy(aiDraw);
    }

    /**
     * Retrieve a paginated list of AiDraw records.
     *
     * @param pageNo   the page number to retrieve
     * @param pageSize the number of records per page
     * @param sortStr  optional sort expression to order the results
     * @param searchStr optional search expression to filter the results
     * @return an IPage of AiDraw containing the requested page of records
     */
    @GetMapping("/page")
    @ApiOperation(value = "分页结果")
    public IPage<AiDraw> getPage(@RequestParam int pageNo, @RequestParam int pageSize,
                                             @RequestParam(value = "sortStr", required = false) String sortStr,
                                             @RequestParam(value = "searchStr", required = false) String searchStr) {
        return aiDrawService.getPage(pageNo, pageSize, sortStr, searchStr);
    }

    /**
     * Uploads the provided original image to OSS and returns its generated object name.
     *
     * @param file the original image file to upload
     * @return the OSS object name where the file was stored (for example "ai/draw/origin/{originalFilename}")
     * @throws IOException if reading the file bytes or uploading to OSS fails
     */
    @PostMapping("/upload")
    @ApiOperation(value = "原图上传")
    public String uploadOriginImg(@RequestBody MultipartFile file) throws IOException {
        String objectName = "ai/draw/origin/"+file.getOriginalFilename();
        ossUtils.uploadToOss(objectName, file.getBytes());
        return objectName;
    }

    /**
     * Create a new AI draw upload history record.
     *
     * @param aiDrawRecord the upload history record to persist
     */
    @PostMapping("/record/new")
    @ApiOperation(value = "图片上传历史")
    public void newRecord(@RequestBody AiDrawRecord aiDrawRecord) {
        aiDrawService.newRecord(aiDrawRecord);
    }

    /**
     * Retrieve a paginated list of image upload history records.
     *
     * @param pageNo   the page number to retrieve
     * @param pageSize the number of records per page
     * @param sortStr  optional sort expression applied to results
     * @param searchStr optional search filter applied to results
     * @return an IPage containing the requested page of AiDrawRecord entries, optionally filtered and sorted
     */
    @GetMapping("/record/page")
    @ApiOperation(value = "分页图片上传历史结果")
    public IPage<AiDrawRecord> getRecordPage(@RequestParam int pageNo, @RequestParam int pageSize,
                                 @RequestParam(value = "sortStr", required = false) String sortStr,
                                 @RequestParam(value = "searchStr", required = false) String searchStr) {
        return aiDrawService.getRecordPage(pageNo, pageSize, sortStr, searchStr);
    }


    /**
     * Creates a new drawing material record.
     *
     * @param AiDrawMaterials the material to create
     */
    @PostMapping("/materials/new")
    @ApiOperation(value = "新增素材")
    public void newMaterials(@RequestBody AiDrawMaterials AiDrawMaterials) {
        aiDrawService.newMaterials(AiDrawMaterials);
    }

    /**
     * Update an existing draw material record.
     *
     * @param AiDrawMaterials the material containing updated fields; its identifier is used to locate the record to modify
     */
    @PostMapping("/materials/update")
    @ApiOperation(value = "修改素材")
    public void updateMaterials(@RequestBody AiDrawMaterials AiDrawMaterials) {
        aiDrawService.updateMaterials(AiDrawMaterials);
    }

    /**
     * Deletes a material entry from the AI draw material library.
     *
     * @param AiDrawMaterials the material to delete; should contain the identifier of the material to remove
     */
    @PostMapping("/materials/delete")
    @ApiOperation(value = "删除素材")
    public void deleteMaterials(@RequestBody AiDrawMaterials AiDrawMaterials) {
        aiDrawService.deleteMaterials(AiDrawMaterials);
    }

    /**
     * Retrieve a paginated list of drawing materials.
     *
     * @param pageNo   the 1-based page number to retrieve
     * @param pageSize the number of items per page
     * @param sortStr  optional sort expression to order results (e.g., "fieldName asc")
     * @param searchStr optional search keyword(s) to filter materials
     * @return an IPage of AiDrawMaterials containing the requested page of materials
     */
    @GetMapping("/materials/page")
    @ApiOperation(value = "分页素材库结果")
    public IPage<AiDrawMaterials> getMaterialsPage(@RequestParam int pageNo, @RequestParam int pageSize,
                                 @RequestParam(value = "sortStr", required = false) String sortStr,
                                 @RequestParam(value = "searchStr", required = false) String searchStr) {
        return aiDrawService.getMaterialsPage(pageNo, pageSize, sortStr, searchStr);
    }

    /**
     * Fetches and returns image URLs for the provided draw request.
     *
     * @param aiDraw the draw request containing URL parameters used to locate and fetch images
     * @return a list of fetched image URLs
     * @throws IOException if an I/O error occurs while fetching or transferring images
     */
    @PostMapping("/transfer")
    @ApiOperation(value = "中转")
    public List<String> transfer(@RequestBody AiDraw aiDraw) throws IOException {
        return aiDrawService.fetchImageUrls(aiDraw.getUrlParam(),null);
    }

    /**
     * Forwards the request's URL parameters to the Gemini transfer operation.
     *
     * @param aiDraw request body containing URL parameters; the value returned by {@code aiDraw.getUrlParam()} is used for the transfer
     * @return the response string produced by the Gemini transfer operation
     * @throws IOException if an I/O error occurs while performing the transfer
     */
    @PostMapping("/transfer/gemini")
    @ApiOperation(value = "中转gemini模型")
    public String transferGemini(@RequestBody AiDraw aiDraw) throws IOException {
        return aiDrawService.transferGemini(aiDraw.getUrlParam());
    }
}
