package com.cyanrocks.ai.controller;

import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ai.dao.entity.AiFirmWechatForm;
import com.cyanrocks.ai.dao.entity.WecomContacts;
import com.cyanrocks.ai.dao.mapper.WecomContactsMapper;
import com.cyanrocks.ai.service.QywxService;
import com.cyanrocks.ai.service.WecomContactsService;
import com.cyanrocks.ai.vo.ContactDetailVO;
import com.cyanrocks.ai.vo.request.QywxOrderLinkReqVO;
import com.cyanrocks.ai.vo.request.QywxUserInfoReqVO;
import com.cyanrocks.ai.vo.response.GenericResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信接口控制器
 *
 * @author yangshihao
 */
@Slf4j
@RestController
@RequestMapping("/ai/qywx")
@Api(tags = {"企业微信接口"})
@CrossOrigin(origins = "*")
public class QywxController {

    @Autowired
    private QywxService qywxService;
    @Autowired
    private WecomContactsService wecomContactsService;

    @Autowired
    private WecomContactsMapper wecomContactsMapper;


    @PostMapping("/from")
    @ApiOperation(value = "用户填写表单信息")
    public Boolean savefrom(@RequestBody AiFirmWechatForm firmWechatForm) {
        // 参数校验
        if (firmWechatForm == null || StringUtils.isBlank(firmWechatForm.getCode())) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("message", "code参数不能为空");
            return false;
        }
        try {
            JSONObject userInfo = qywxService.getUserInfo(firmWechatForm.getCode());
            String externalUserid = userInfo.getString("external_userid");

            // 从 WecomContacts 表中验证 externalUserid 是否已存在
            WecomContacts existingContact = wecomContactsService.getByExternalUserid(externalUserid);
            if (existingContact != null) {
                String customerId = qywxService.getCustomerIdByOtid(firmWechatForm.getOtid());
                if (StringUtils.isNotEmpty(customerId)) {
                    existingContact.setCustomerId(customerId);
                    existingContact.setState("active");
                    existingContact.setUpdatedAt(LocalDateTime.now());
                    wecomContactsMapper.updateById(existingContact);
                    log.info("externalUserid 已存在于 WecomContacts 表中，更新customeId: externalUserid={}, otid={}",
                            externalUserid, firmWechatForm.getOtid());
                }

                return true;
            }
            WecomContacts wecomContacts = new WecomContacts();
            wecomContacts.setExternalUserid(externalUserid);
            // 通过订单号(otid)调用OMS接口获取客户ID
            String customerId = qywxService.getCustomerIdByOtid(firmWechatForm.getOtid());
            if (customerId != null && !customerId.isEmpty()) {
                wecomContacts.setCustomerId(customerId);
                wecomContacts.setState("active");
            } else {
                log.warn("无法获取客户ID, otid={}", firmWechatForm.getOtid());
                wecomContacts.setCustomerId(null);
                wecomContacts.setState("lost");
            }
            // 获取客户标签和添加时间（从企微接口获取）
            ContactDetailVO contactDetailVO = qywxService.getContactDetailInfo(externalUserid);
            wecomContacts.setTags(contactDetailVO.getTags());
            if (contactDetailVO.getCreateTime() != null) {
                wecomContacts.setAddTime(contactDetailVO.getCreateTime());  // 设置添加时间
            }
            String remarkCompany = externalUserid.length() > 20
                    ? externalUserid.substring(externalUserid.length() - 20)
                    : externalUserid;
            wecomContacts.setFollowUserid(firmWechatForm.getUserId());
            wecomContacts.setCompanyId(remarkCompany);
            wecomContactsService.insertWecomContact(wecomContacts);
            // 设置客户企业备注（将external_userid截取后20位放到企业信息里）
            qywxService.setCustomerRemarkCompany(firmWechatForm.getUserId(), externalUserid, remarkCompany);
        } catch (Exception e) {
            log.error("保存用户表单信息失败: code={}, otid={}, userId={}",
                    firmWechatForm.getCode(),
                    firmWechatForm.getOtid(),
                    firmWechatForm.getUserId(), e);
            return false;
        }

        return true;
    }

//    /**
//     * 获取用户信息接口（前端调用）
//     * POST /api/qywx/userinfo
//     */
//    @PostMapping("/userinfo")
//    @ApiOperation(value = "获取企业微信用户信息")
//    public GenericResponse<Map<String, Object>> getUserInfo(@RequestBody QywxUserInfoReqVO req) {
//        try {
//            // 参数校验
//            if (req == null || StringUtils.isBlank(req.getCode())) {
//                Map<String, Object> errorResult = new HashMap<>();
//                errorResult.put("message", "code参数不能为空");
//                return GenericResponse.error("code参数不能为空", errorResult);
//            }
//
//            // 获取用户信息
//            JSONObject userInfo = qywxService.getUserInfo(req.getCode());
//
//            Map<String, Object> result = new HashMap<>();
//            result.put("external_userid", userInfo.getString("external_userid"));
//            result.put("data", userInfo);
//
//            return GenericResponse.success(result);
//        } catch (Exception e) {
//            log.error("获取用户信息失败", e);
//            Map<String, Object> errorResult = new HashMap<>();
//            errorResult.put("message", e.getMessage());
//            return GenericResponse.error("获取用户信息失败: " + e.getMessage(), errorResult);
//        }
//    }
//
//    /**
//     * 订单关联接口（前端调用）
//     * POST /ai/qywx/order/link
//     */
//    @PostMapping("/order/link")
//    @ApiOperation(value = "关联订单到企业微信客户")
//    public GenericResponse<Map<String, Object>> linkOrder(@RequestBody QywxOrderLinkReqVO req) {
//        try {
//            // 参数校验
//            if (req == null || StringUtils.isBlank(req.getUserid())) {
//                Map<String, Object> errorResult = new HashMap<>();
//                errorResult.put("message", "userid参数不能为空");
//                return GenericResponse.error("userid参数不能为空", errorResult);
//            }
//            if (StringUtils.isBlank(req.getExternalUserid())) {
//                Map<String, Object> errorResult = new HashMap<>();
//                errorResult.put("message", "external_userid参数不能为空");
//                return GenericResponse.error("external_userid参数不能为空", errorResult);
//            }
//            if (StringUtils.isBlank(req.getOrderNo())) {
//                Map<String, Object> errorResult = new HashMap<>();
//                errorResult.put("message", "orderNo参数不能为空");
//                return GenericResponse.error("orderNo参数不能为空", errorResult);
//            }
//
//            // 设置客户企业备注（将external_userid放到企业信息里）
//            qywxService.setCustomerRemarkCompany(req.getUserid(), req.getExternalUserid());
//
//            // 保存订单关联数据到数据库
//            qywxService.saveOrderLink(req.getExternalUserid(), req.getOrderNo());
//
//            Map<String, Object> result = new HashMap<>();
//            result.put("message", "订单关联成功");
//
//            return GenericResponse.success(result);
//        } catch (Exception e) {
//            log.error("订单关联失败", e);
//            Map<String, Object> errorResult = new HashMap<>();
//            errorResult.put("message", e.getMessage());
//            return GenericResponse.error("订单关联失败: " + e.getMessage(), errorResult);
//        }
//    }
}
