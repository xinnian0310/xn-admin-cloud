package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.dto.ApiRegistryVO;
import com.smartadmin.dto.AuthUserVO;
import com.smartadmin.dto.CaptchaVO;
import com.smartadmin.dto.ChangePasswordRequest;
import com.smartadmin.dto.LoginRequest;
import com.smartadmin.dto.LoginResponse;
import com.smartadmin.dto.PasswordRulesVO;
import com.smartadmin.dto.ProfileUpdateRequest;
import com.smartadmin.dto.SliderVerifyRequest;
import com.smartadmin.security.ApiPermissionRegistry;
import com.smartadmin.service.AuthService;
import com.smartadmin.service.CaptchaService;
import com.smartadmin.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RouteService routeService;
    private final ApiPermissionRegistry apiPermissionRegistry;
    private final CaptchaService captchaService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return ApiResponse.success(null);
    }

    /** 获取登录验证码（登录页未开启时 data 为 null） */
    @GetMapping("/captcha")
    public ApiResponse<CaptchaVO> captcha() {
        return ApiResponse.success(captchaService.create());
    }

    /** 滑块验证通过后标记 captchaId 为已验证 */
    @PostMapping("/captcha/slider")
    public ApiResponse<Void> verifySlider(@Valid @RequestBody SliderVerifyRequest request) {
        captchaService.verifySlider(request.getCaptchaId(), request.getPercent());
        return ApiResponse.success(null);
    }

    /** 滑动续期：携带有效 JWT 换发新 token，延长固定过期时间 */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh() {
        return ApiResponse.success(authService.refresh());
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserVO> me() {
        return ApiResponse.success(authService.currentUser());
    }

    /** 更新当前用户个人信息（超级管理员禁止） */
    @PutMapping("/me")
    public ApiResponse<AuthUserVO> updateMe(@Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.success(authService.updateProfile(request));
    }

    /** 当前生效密码规则（表单提示） */
    @GetMapping("/password-rules")
    public ApiResponse<PasswordRulesVO> passwordRules() {
        return ApiResponse.success(authService.passwordRules());
    }

    /** 修改密码（需校验原密码） */
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.success("密码已修改", null);
    }

    /** 上传头像 */
    @PostMapping("/me/avatar")
    public ApiResponse<AuthUserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("上传成功", authService.uploadAvatar(file));
    }

    @GetMapping("/menus")
    public ApiResponse<List<com.smartadmin.dto.RouteVO>> menus() {
        return ApiResponse.success(routeService.menuTreeForCurrentUser());
    }

    /** 权限内容注册表：已登记接口签名 + 全部权限编码，供前端做接口/按钮校验 */
    @GetMapping("/api-registry")
    public ApiResponse<ApiRegistryVO> apiRegistry() {
        return ApiResponse.success(apiPermissionRegistry.snapshot());
    }
}
