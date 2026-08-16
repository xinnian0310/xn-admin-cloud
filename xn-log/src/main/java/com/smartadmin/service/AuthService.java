package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.common.WebUtils;
import com.smartadmin.dto.AuthUserVO;
import com.smartadmin.dto.ChangePasswordRequest;
import com.smartadmin.dto.LoginRequest;
import com.smartadmin.dto.LoginResponse;
import com.smartadmin.dto.ProfileUpdateRequest;
import com.smartadmin.dto.RegisterRequest;
import com.smartadmin.entity.Role;
import com.smartadmin.entity.User;
import com.smartadmin.repository.RoleRepository;
import com.smartadmin.repository.UserRepository;
import com.smartadmin.security.JwtUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<String> ALLOWED_AVATAR_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;
    private final RbacService rbacService;
    private final PasswordEncoder passwordEncoder;
    private final LoginLogService loginLogService;
    private final CaptchaService captchaService;
    private final LoginProtectService loginProtectService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordPolicyService passwordPolicyService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public LoginResponse login(LoginRequest request) {
        String ip = WebUtils.getClientIp();
        String userAgent = WebUtils.getUserAgent();
        loginProtectService.checkBeforeLogin(request.getUsername(), ip);
        captchaService.validateForLogin(request.getCaptchaId(), request.getCaptchaCode());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword()));
        } catch (Exception ex) {
            loginProtectService.onLoginFail(request.getUsername());
            loginLogService.record(request.getUsername(), ip, userAgent, 0, "用户名或密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        User user =
                userRepository
                        .findByUsernameWithRolesIgnoreCase(request.getUsername())
                        .orElse(null);
        if (user == null) {
            loginProtectService.onLoginFail(request.getUsername());
            loginLogService.record(request.getUsername(), ip, userAgent, 0, "用户不存在");
            throw new BusinessException("用户不存在");
        }

        if (user.getStatus() != 1) {
            loginLogService.record(user.getUsername(), ip, userAgent, 0, "账号已被禁用");
            throw new BusinessException("账号已被禁用");
        }
        if (user.getDeletedAt() != null) {
            loginLogService.record(user.getUsername(), ip, userAgent, 0, "账号已被删除");
            throw new BusinessException("用户名或密码错误");
        }

        loginProtectService.onLoginSuccess(user.getUsername());
        String token =
                jwtUtil.generateToken(
                        user.getId(), user.getUsername(), rbacService.getRoleCodes(user));
        AuthUserVO authUser = buildAuthUser(user);
        loginLogService.record(user.getUsername(), ip, userAgent, 1, "登录成功");
        return new LoginResponse(token, authUser);
    }

    /** 公开注册：固定分配普通用户（USER）角色 */
    @Transactional
    public void register(RegisterRequest request) {
        captchaService.validateForLogin(request.getCaptchaId(), request.getCaptchaCode());

        String username = request.getUsername().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException("用户名已存在");
        }

        Role userRole =
                roleRepository
                        .findByCode("USER")
                        .orElseThrow(() -> new BusinessException("普通用户角色未配置，请联系管理员"));

        User user = new User();
        user.setUsername(username);
        user.setNickname(
                StringUtils.hasText(request.getNickname())
                        ? request.getNickname().trim()
                        : username);
        user.setStatus(1);
        user.setRoles(new HashSet<>(Set.of(userRole)));
        rbacService.syncLegacyRoleField(user);
        passwordPolicyService.assignPassword(user, request.getPassword().trim(), false);
        userRepository.save(user);
    }

    /** 登出：将当前令牌加入黑名单 */
    public void logout(String bearerToken) {
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            tokenBlacklistService.revokeToken(bearerToken.substring(7).trim());
        } else if (StringUtils.hasText(bearerToken)) {
            tokenBlacklistService.revokeToken(bearerToken.trim());
        }
    }

    /** 滑动续期：校验当前登录态后签发新 JWT */
    public LoginResponse refresh() {
        User user = rbacService.currentUser();
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }
        String token =
                jwtUtil.generateToken(
                        user.getId(), user.getUsername(), rbacService.getRoleCodes(user));
        return new LoginResponse(token, buildAuthUser(user));
    }

    public AuthUserVO currentUser() {
        User user = rbacService.currentUser();
        return buildAuthUser(user);
    }

    /** 更新当前登录用户资料；超管与管理员禁止修改 */
    @Transactional
    public AuthUserVO updateProfile(ProfileUpdateRequest request) {
        User user = rbacService.currentUser();
        if (rbacService.isSuperAdmin(user) || rbacService.isAdmin(user)) {
            throw new BusinessException("管理员禁止编辑个人信息");
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (StringUtils.hasText(request.getPassword())) {
            passwordPolicyService.assignPassword(user, request.getPassword(), false);
        }
        userRepository.save(user);
        User refreshed = userRepository.findByIdWithRoles(user.getId()).orElse(user);
        return buildAuthUser(refreshed);
    }

    /** 修改密码：校验原密码 + 密码策略 */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = rbacService.currentUser();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        passwordPolicyService.assignPassword(user, request.getNewPassword(), false);
        userRepository.save(user);
    }

    /** 当前生效密码规则（登录用户均可读，用于表单提示） */
    public com.smartadmin.dto.PasswordRulesVO passwordRules() {
        return passwordPolicyService.rules();
    }

    /** 上传头像，返回可访问 URL */
    @Transactional
    public AuthUserVO uploadAvatar(MultipartFile file) {
        User user = rbacService.currentUser();
        if (rbacService.isSuperAdmin(user) || rbacService.isAdmin(user)) {
            throw new BusinessException("管理员禁止编辑个人信息");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择头像文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType)) {
            throw new BusinessException("仅支持 jpg/png/gif/webp 图片");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException("头像大小不能超过 2MB");
        }
        String ext = resolveAvatarExt(file.getOriginalFilename(), contentType);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path dir = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename));
        } catch (IOException e) {
            throw new BusinessException("头像上传失败");
        }
        user.setAvatar("/uploads/avatars/" + filename);
        userRepository.save(user);
        User refreshed = userRepository.findByIdWithRoles(user.getId()).orElse(user);
        return buildAuthUser(refreshed);
    }

    private String resolveAvatarExt(String originalFilename, String contentType) {
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            String ext =
                    originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
            if (ext.matches("\\.(jpg|jpeg|png|gif|webp)")) {
                return ext.equals(".jpeg") ? ".jpg" : ext;
            }
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private AuthUserVO buildAuthUser(User user) {
        return AuthUserVO.from(
                user,
                rbacService.getRoleCodes(user),
                rbacService.getPermissionCodes(user),
                passwordPolicyService.mustChangePassword(user));
    }
}
