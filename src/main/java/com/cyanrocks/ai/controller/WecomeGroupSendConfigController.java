package com.cyanrocks.ai.controller;

import com.cyanrocks.ai.dao.entity.WecomeGroupSendConfig;
import com.cyanrocks.ai.service.WecomeGroupSendConfigService;
import com.cyanrocks.ai.utils.OssUtils;
import com.cyanrocks.ai.vo.response.GenericResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 企微群发策略配置控制器
 * 提供群发策略的增删改查接口，实现策略动态管理
 * @author yangshihao
 */
@Slf4j
@RestController
@RequestMapping("/ai/wecom/group-send")
@Api(tags = {"企微群发策略配置接口"})
@CrossOrigin(origins = "*")
public class WecomeGroupSendConfigController {

    @Autowired
    private WecomeGroupSendConfigService wecomeGroupSendConfigService;

    @Autowired
    private OssUtils ossUtils;

    // ==================== 图片上传接口 ====================

    /**
     * 上传群发图片
     * 上传图片到OSS，返回图片URL，前端拿到URL后可调用 update/image 接口存储到策略配置
     */
    @PostMapping("/upload/image")
    @ApiOperation(value = "上传群发图片")
    public GenericResponse<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return GenericResponse.error("上传文件不能为空", (Map<String, String>) null);
            }

            // 生成OSS对象名
            String originalFilename = file.getOriginalFilename();
            String objectName = "wecom/group-send/" + System.currentTimeMillis() + "_" + originalFilename;

            // 上传到OSS
            ossUtils.uploadToOss(objectName, file.getBytes());

            // 获取访问URL
            String imageUrl = ossUtils.downloadUrl(objectName);

            Map<String, String> result = new HashMap<>();
            result.put("objectName", objectName);
            result.put("imageUrl", imageUrl);

            return GenericResponse.success(result);
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            return GenericResponse.error("读取文件失败: " + e.getMessage(), (Map<String, String>) null);
        } catch (Exception e) {
            log.error("上传图片失败", e);
            return GenericResponse.error("上传失败: " + e.getMessage(), (Map<String, String>) null);
        }
    }

    // ==================== 查询接口 ====================

    /**
     * 查询所有有效策略（未删除的）
     */
    @GetMapping("/list")
    @ApiOperation(value = "查询所有有效策略")
    public GenericResponse<List<WecomeGroupSendConfig>> list() {
        try {
            List<WecomeGroupSendConfig> configs = wecomeGroupSendConfigService.listValid();
            return GenericResponse.success(configs);
        } catch (Exception e) {
            log.error("查询群发策略列表失败", e);
            return GenericResponse.error("查询失败: " + e.getMessage(), (List<WecomeGroupSendConfig>) null);
        }
    }

    /**
     * 查询所有已启用的策略（enabled=1）
     * 定时任务实际执行的策略列表
     */
    @GetMapping("/list/enabled")
    @ApiOperation(value = "查询所有已启用的策略")
    public GenericResponse<List<WecomeGroupSendConfig>> listEnabled() {
        try {
            List<WecomeGroupSendConfig> configs = wecomeGroupSendConfigService.listEnabled();
            return GenericResponse.success(configs);
        } catch (Exception e) {
            log.error("查询已启用策略失败", e);
            return GenericResponse.error("查询失败: " + e.getMessage(), (List<WecomeGroupSendConfig>) null);
        }
    }

    /**
     * 根据ID查询策略详情
     */
    @GetMapping("/detail/{id}")
    @ApiOperation(value = "根据ID查询策略详情")
    public GenericResponse<WecomeGroupSendConfig> getById(@PathVariable Long id) {
        try {
            WecomeGroupSendConfig config = wecomeGroupSendConfigService.getById(id);
            if (config == null) {
                return GenericResponse.error("策略不存在", (WecomeGroupSendConfig) null);
            }
            return GenericResponse.success(config);
        } catch (Exception e) {
            log.error("查询群发策略详情失败，id={}", id, e);
            return GenericResponse.error("查询失败: " + e.getMessage(), (WecomeGroupSendConfig) null);
        }
    }

    /**
     * 根据daysAgo查询策略列表
     */
    @GetMapping("/listByDaysAgo")
    @ApiOperation(value = "根据daysAgo查询策略列表")
    public GenericResponse<List<WecomeGroupSendConfig>> listByDaysAgo(@RequestParam Integer daysAgo) {
        try {
            if (daysAgo == null || daysAgo < 0) {
                return GenericResponse.error("daysAgo必须为非负整数", (List<WecomeGroupSendConfig>) null);
            }
            List<WecomeGroupSendConfig> configs = wecomeGroupSendConfigService.listByDaysAgo(daysAgo);
            return GenericResponse.success(configs);
        } catch (Exception e) {
            log.error("根据daysAgo查询群发策略失败，daysAgo={}", daysAgo, e);
            return GenericResponse.error("查询失败: " + e.getMessage(), (List<WecomeGroupSendConfig>) null);
        }
    }

    // ==================== 新增接口 ====================

    /**
     * 新增群发策略
     */
    @PostMapping("/add")
    @ApiOperation(value = "新增群发策略")
    public GenericResponse<WecomeGroupSendConfig> add(@RequestBody WecomeGroupSendConfig config) {
        try {
            // 参数校验
            if (StringUtils.isBlank(config.getSender())) {
                return GenericResponse.error("发送者(sender)不能为空", (WecomeGroupSendConfig) null);
            }
            if (StringUtils.isBlank(config.getContent())) {
                return GenericResponse.error("群发文案(content)不能为空", (WecomeGroupSendConfig) null);
            }
            // 核心字段校验
            if (config.getDaysAgo() == null || config.getDaysAgo() < 0) {
                return GenericResponse.error("核心字段daysAgo必须为非负整数", (WecomeGroupSendConfig) null);
            }

            WecomeGroupSendConfig result = wecomeGroupSendConfigService.insert(config);
            if (result != null) {
                return GenericResponse.success(result);
            }
            return GenericResponse.error("新增失败", (WecomeGroupSendConfig) null);
        } catch (Exception e) {
            log.error("新增群发策略失败", e);
            return GenericResponse.error("新增失败: " + e.getMessage(), (WecomeGroupSendConfig) null);
        }
    }

    // ==================== 修改接口 ====================

    /**
     * 更新群发策略
     * 支持动态修改daysAgo等核心参数，修改后下次定时任务执行时生效
     */
    @PostMapping("/update")
    @ApiOperation(value = "更新群发策略")
    public GenericResponse<WecomeGroupSendConfig> update(@RequestBody WecomeGroupSendConfig config) {
        try {
            // 参数校验
            if (config.getId() == null) {
                return GenericResponse.error("策略ID不能为空", (WecomeGroupSendConfig) null);
            }

            // 检查是否存在
            WecomeGroupSendConfig existing = wecomeGroupSendConfigService.getById(config.getId());
            if (existing == null) {
                return GenericResponse.error("策略不存在", (WecomeGroupSendConfig) null);
            }

            // 校验核心字段daysAgo
            if (config.getDaysAgo() != null && config.getDaysAgo() < 0) {
                return GenericResponse.error("核心字段daysAgo必须为非负整数", (WecomeGroupSendConfig) null);
            }

            WecomeGroupSendConfig result = wecomeGroupSendConfigService.update(config);
            if (result != null) {
                return GenericResponse.success(result);
            }
            return GenericResponse.error("更新失败", (WecomeGroupSendConfig) null);
        } catch (Exception e) {
            log.error("更新群发策略失败", e);
            return GenericResponse.error("更新失败: " + e.getMessage(), (WecomeGroupSendConfig) null);
        }
    }

    /**
     * 单独更新daysAgo字段（核心字段快捷修改）
     * 用于快速调整群发触发天数
     */
    @PostMapping("/update/daysAgo")
    @ApiOperation(value = "单独更新daysAgo字段（核心字段）")
    public GenericResponse<Map<String, Object>> updateDaysAgo(@RequestParam Long id, @RequestParam Integer daysAgo) {
        try {
            if (id == null) {
                return GenericResponse.error("策略ID不能为空", (Map<String, Object>) null);
            }
            if (daysAgo == null || daysAgo < 0) {
                return GenericResponse.error("daysAgo必须为非负整数", (Map<String, Object>) null);
            }

            boolean success = wecomeGroupSendConfigService.updateDaysAgo(id, daysAgo);
            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("daysAgo", daysAgo);
            result.put("success", success);
            if (success) {
                result.put("message", "daysAgo已更新，下次定时任务执行时生效");
                return GenericResponse.success(result);
            }
            return GenericResponse.error("更新失败，策略不存在", result);
        } catch (Exception e) {
            log.error("更新daysAgo失败，id={}, daysAgo={}", id, daysAgo, e);
            return GenericResponse.error("更新失败: " + e.getMessage(), (Map<String, Object>) null);
        }
    }

    /**
     * 更新图片URL（上传图片后，将返回的imageUrl存储到策略配置）
     */
    @PostMapping("/update/image")
    @ApiOperation(value = "更新图片URL")
    public GenericResponse<Map<String, Object>> updateImageUrl(@RequestParam Long id, @RequestParam String imageUrl) {
        try {
            if (id == null) {
                return GenericResponse.error("策略ID不能为空", (Map<String, Object>) null);
            }
            if (StringUtils.isBlank(imageUrl)) {
                return GenericResponse.error("图片URL不能为空", (Map<String, Object>) null);
            }

            boolean success = wecomeGroupSendConfigService.updateImageUrl(id, imageUrl);
            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("imageUrl", imageUrl);
            result.put("success", success);
            if (success) {
                result.put("message", "图片已更新");
                return GenericResponse.success(result);
            }
            return GenericResponse.error("更新失败，策略不存在", result);
        } catch (Exception e) {
            log.error("更新图片URL失败，id={}", id, e);
            return GenericResponse.error("更新失败: " + e.getMessage(), (Map<String, Object>) null);
        }
    }

    /**
     * 启用/禁用策略
     */
    @PostMapping("/update/enabled")
    @ApiOperation(value = "启用/禁用策略")
    public GenericResponse<Map<String, Object>> updateEnabled(@RequestParam Long id, @RequestParam Integer enabled) {
        try {
            if (id == null) {
                return GenericResponse.error("策略ID不能为空", (Map<String, Object>) null);
            }
            if (enabled != 0 && enabled != 1) {
                return GenericResponse.error("enabled必须为0或1", (Map<String, Object>) null);
            }

            WecomeGroupSendConfig config = wecomeGroupSendConfigService.getById(id);
            if (config == null) {
                return GenericResponse.error("策略不存在", (Map<String, Object>) null);
            }

            config.setEnabled(enabled);
            boolean success = wecomeGroupSendConfigService.update(config) != null;

            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("enabled", enabled);
            result.put("success", success);
            if (success) {
                result.put("message", enabled == 1 ? "策略已启用" : "策略已禁用");
                return GenericResponse.success(result);
            }
            return GenericResponse.error("更新失败", result);
        } catch (Exception e) {
            log.error("更新enabled失败，id={}, enabled={}", id, enabled, e);
            return GenericResponse.error("更新失败: " + e.getMessage(), (Map<String, Object>) null);
        }
    }

    // ==================== 删除接口 ====================

    /**
     * 删除群发策略（逻辑删除）
     * 更新is_del=1，不物理删除数据
     */
    @PostMapping("/delete/{id}")
    @ApiOperation(value = "删除群发策略（逻辑删除）")
    public GenericResponse<Map<String, Object>> delete(@PathVariable Long id) {
        try {
            boolean success = wecomeGroupSendConfigService.deleteById(id);
            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("success", success);
            if (success) {
                return GenericResponse.success(result);
            }
            return GenericResponse.error("删除失败，策略不存在", result);
        } catch (Exception e) {
            log.error("删除群发策略失败，id={}", id, e);
            return GenericResponse.error("删除失败: " + e.getMessage(), (Map<String, Object>) null);
        }
    }
}
