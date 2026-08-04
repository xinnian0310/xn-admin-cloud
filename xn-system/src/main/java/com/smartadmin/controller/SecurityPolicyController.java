package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.LockedAccountVO;
import com.smartadmin.dto.SecurityPolicyVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.SecurityPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/security-policy")
@RequiredArgsConstructor
public class SecurityPolicyController {

    private final SecurityPolicyService securityPolicyService;

    @GetMapping
    public ApiResponse<SecurityPolicyVO> get() {
        return ApiResponse.success(securityPolicyService.get());
    }

    @PutMapping
    @OperLog(title = "安全策略", businessType = OperBusinessType.UPDATE)
    public ApiResponse<SecurityPolicyVO> update(@Valid @RequestBody SecurityPolicyVO request) {
        return ApiResponse.success("保存成功", securityPolicyService.update(request));
    }

    @GetMapping("/locks")
    public ApiResponse<List<LockedAccountVO>> locks() {
        return ApiResponse.success(securityPolicyService.listLocked());
    }

    @DeleteMapping("/locks/{username}")
    @OperLog(title = "安全策略-解锁账号", businessType = OperBusinessType.UPDATE)
    public ApiResponse<Void> unlock(@PathVariable String username) {
        securityPolicyService.unlock(username);
        return ApiResponse.success("已解锁", null);
    }
}
