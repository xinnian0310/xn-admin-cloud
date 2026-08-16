package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.ImportResultVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.dto.UserImportRow;
import com.smartadmin.dto.UserRequest;
import com.smartadmin.dto.UserVO;
import com.smartadmin.entity.Role;
import com.smartadmin.entity.SysPost;
import com.smartadmin.entity.SysUnit;
import com.smartadmin.entity.User;
import com.smartadmin.repository.RoleRepository;
import com.smartadmin.repository.SysPostRepository;
import com.smartadmin.repository.SysUnitRepository;
import com.smartadmin.repository.UserRepository;
import com.smartadmin.util.ExcelExportUtil;
import com.smartadmin.util.SensitiveDataUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DEFAULT_IMPORT_PASSWORD = "User123456";
    private static final int EXPORT_LIMIT = 10000;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SysUnitRepository unitRepository;
    private final SysPostRepository postRepository;
    private final RbacService rbacService;
    private final UnitService unitService;
    private final DataScopeService dataScopeService;
    private final AppCacheService appCacheService;
    private final RecycleService recycleService;
    private final PasswordPolicyService passwordPolicyService;
    private final AppConfigService appConfigService;

    public PageResult<UserVO> list(int page, int size, String keyword, Long roleId, Long unitId) {
        rbacService.checkPermission("user:view");
        PageRequest pageable = PageRequest.of(page, size);
        DataScopeService.ResolvedScope scope = dataScopeService.resolveCurrent();
        DataScopeService.UnitFilter filter = dataScopeService.applyUnitFilter(scope, unitId);

        Long selfUserId = filter.selfOnly() ? scope.selfUserId() : null;
        boolean unitIdsEmpty = filter.unrestricted();
        List<Long> unitIds = unitIdsEmpty ? List.of(-1L) : filter.unitIds();

        Page<User> result =
                userRepository.search(
                        StringUtils.hasText(keyword) ? keyword : "",
                        roleId,
                        unitIds,
                        unitIdsEmpty,
                        selfUserId,
                        pageable);
        List<UserVO> records = result.getContent().stream().map(this::toUserVO).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public UserVO getById(Long id) {
        rbacService.checkPermission("user:view");
        User user = findUserWithRoles(id);
        dataScopeService.assertUserAccessible(user);
        return toUserVO(user);
    }

    @Transactional
    public UserVO create(UserRequest request) {
        rbacService.checkPermission("user:create");
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        resolveRoleIds(request);
        rbacService.validateUserEffectiveRoles(request.getRoleIds(), request.getUnitId());

        User user = new User();
        applyRequest(user, request, true);
        User saved = userRepository.save(user);
        appCacheService.evictPermissionCodes(saved.getId());
        return toUserVO(saved);
    }

    @Transactional
    public UserVO update(Long id, UserRequest request) {
        rbacService.checkPermission("user:update");
        User user = findUserWithRoles(id);
        dataScopeService.assertUserAccessible(user);
        if (isSeedAccount(user.getUsername())
                && !user.getUsername().equalsIgnoreCase(request.getUsername())) {
            throw new BusinessException("不能修改默认管理员账号的用户名");
        }
        if (isSeedAccount(user.getUsername())) {
            // 固定种子账号规范用户名，避免大小写被改写导致登录态异常
            request.setUsername(user.getUsername());
        }
        if (!user.getUsername().equals(request.getUsername())
                && userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        resolveRoleIds(request);
        rbacService.validateUserEffectiveRoles(request.getRoleIds(), request.getUnitId());
        List<Long> roleIds = request.getRoleIds() != null ? request.getRoleIds() : List.of();
        rbacService.ensureSuperAdminExists(user, roleIds);
        applyRequest(user, request, false);
        User saved = userRepository.save(user);
        appCacheService.evictPermissionCodes(saved.getId());
        return toUserVO(saved);
    }

    @Transactional
    public ImportResultVO importUsers(List<UserImportRow> rows) {
        rbacService.checkPermission("user:import");
        ImportResultVO result = new ImportResultVO();
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("导入数据为空");
        }
        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2; // 模板第 1 行表头
            UserImportRow row = rows.get(i);
            try {
                importOne(row);
                result.addSuccess();
            } catch (BusinessException ex) {
                result.addError(rowNum, ex.getMessage());
            } catch (Exception ex) {
                result.addError(rowNum, "导入失败: " + ex.getMessage());
            }
        }
        return result;
    }

    private void importOne(UserImportRow row) {
        if (row == null || !StringUtils.hasText(row.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        String username = row.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("用户名已存在: " + username);
        }

        UserRequest request = new UserRequest();
        request.setUsername(username);
        String password =
                StringUtils.hasText(row.getPassword())
                        ? row.getPassword().trim()
                        : DEFAULT_IMPORT_PASSWORD;
        passwordPolicyService.validateComplexity(password);
        request.setPassword(password);
        request.setNickname(
                StringUtils.hasText(row.getNickname()) ? row.getNickname().trim() : username);
        request.setEmail(row.getEmail() != null ? row.getEmail().trim() : null);
        request.setPhone(row.getPhone() != null ? row.getPhone().trim() : null);
        request.setStatus(row.getStatus() != null ? row.getStatus() : 1);

        if (StringUtils.hasText(row.getUnitCode())) {
            SysUnit unit =
                    unitRepository
                            .findByCode(row.getUnitCode().trim())
                            .orElseThrow(
                                    () -> new BusinessException("单位编码不存在: " + row.getUnitCode()));
            request.setUnitId(unit.getId());
        }

        if (StringUtils.hasText(row.getPostCode())) {
            SysPost post =
                    postRepository
                            .findByCode(row.getPostCode().trim())
                            .orElseThrow(
                                    () -> new BusinessException("岗位编码不存在: " + row.getPostCode()));
            request.setPostId(post.getId());
        }

        List<Long> roleIds = new ArrayList<>();
        if (StringUtils.hasText(row.getRoleCodes())) {
            List<String> codes =
                    Arrays.stream(row.getRoleCodes().split("[,，;；\\s]+"))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .distinct()
                            .toList();
            for (String code : codes) {
                Role role =
                        roleRepository
                                .findByCode(code)
                                .orElseThrow(() -> new BusinessException("角色编码不存在: " + code));
                roleIds.add(role.getId());
            }
        }
        request.setRoleIds(roleIds);
        rbacService.validateUserEffectiveRoles(request.getRoleIds(), request.getUnitId());

        User user = new User();
        applyRequest(user, request, true);
        userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("user:delete");
        deleteInternal(id);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("user:delete");
        int count = 0;
        for (Long id : ids) {
            deleteInternal(id);
            count++;
        }
        return count;
    }

    private void deleteInternal(Long id) {
        User user = findUserWithRoles(id);
        dataScopeService.assertUserAccessible(user);
        if ("SuperAdmin".equalsIgnoreCase(user.getUsername())
                || "admin".equalsIgnoreCase(user.getUsername())) {
            throw new BusinessException("不能删除默认管理员账号");
        }
        if (rbacService.isSuperAdmin(user)) {
            long count = userRepository.countActiveSuperAdmins();
            if (count <= 1) {
                throw new BusinessException("不能删除最后一个超级管理员");
            }
        }
        recycleService.softDeleteUser(user);
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        rbacService.checkPermission("user:update");
        User user = findUserWithRoles(id);
        dataScopeService.assertUserAccessible(user);
        rbacService.ensureCanDisableSuperAdmin(user, status);
        if (("SuperAdmin".equalsIgnoreCase(user.getUsername())
                        || "admin".equalsIgnoreCase(user.getUsername()))
                && status == 0) {
            throw new BusinessException("不能禁用默认管理员账号");
        }
        user.setStatus(status);
        userRepository.save(user);
    }

    public List<UserVO> listAllSimple() {
        return userRepository.findAll().stream()
                .filter(dataScopeService::isUserAccessible)
                .map(this::toUserVO)
                .toList();
    }

    private void resolveRoleIds(UserRequest request) {
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            return;
        }
        if (StringUtils.hasText(request.getRole())) {
            Role role =
                    roleRepository
                            .findByCode(request.getRole())
                            .orElseThrow(
                                    () -> new BusinessException("角色不存在: " + request.getRole()));
            request.setRoleIds(List.of(role.getId()));
            return;
        }
        if (request.getRoleIds() == null) {
            request.setRoleIds(List.of());
        }
    }

    private void applyRequest(User user, UserRequest request, boolean isCreate) {
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        // 无敏感查看权限时接口返回掩码；更新时跳过含 * 的值，避免把打码写回库
        if (isCreate || !SensitiveDataUtil.isMasked(request.getEmail())) {
            user.setEmail(request.getEmail());
        }
        if (isCreate || !SensitiveDataUtil.isMasked(request.getPhone())) {
            user.setPhone(request.getPhone());
        }
        user.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        List<Long> roleIds = request.getRoleIds() != null ? request.getRoleIds() : List.of();
        if (roleIds.isEmpty()) {
            user.setRoles(new HashSet<>());
            user.setRole("USER");
        } else {
            Set<Role> roles = rbacService.loadRolesByIds(roleIds);
            user.setRoles(roles);
            rbacService.syncLegacyRoleField(user);
        }
        if (request.getUnitId() != null) {
            SysUnit unit =
                    unitRepository
                            .findById(request.getUnitId())
                            .orElseThrow(() -> new BusinessException("单位不存在"));
            user.setUnit(unit);
        } else {
            user.setUnit(null);
        }
        if (request.getPostId() != null) {
            SysPost post =
                    postRepository
                            .findById(request.getPostId())
                            .orElseThrow(() -> new BusinessException("岗位不存在"));
            user.setPost(post);
        } else {
            user.setPost(null);
        }
        if (StringUtils.hasText(request.getPassword())) {
            passwordPolicyService.assignPassword(user, request.getPassword().trim(), true);
        } else if (isCreate) {
            throw new BusinessException("密码不能为空");
        }
    }

    public byte[] exportExcel(String keyword, Long roleId, Long unitId) {
        rbacService.checkPermission("user:export");
        DataScopeService.ResolvedScope scope = dataScopeService.resolveCurrent();
        DataScopeService.UnitFilter filter = dataScopeService.applyUnitFilter(scope, unitId);
        Long selfUserId = filter.selfOnly() ? scope.selfUserId() : null;
        boolean unitIdsEmpty = filter.unrestricted();
        List<Long> unitIds = unitIdsEmpty ? List.of(-1L) : filter.unitIds();

        PageRequest pageable = PageRequest.of(0, EXPORT_LIMIT);
        Page<User> result =
                userRepository.search(
                        StringUtils.hasText(keyword) ? keyword : "",
                        roleId,
                        unitIds,
                        unitIdsEmpty,
                        selfUserId,
                        pageable);
        List<User> rows = result.getContent();
        return ExcelExportUtil.toXlsx(
                "用户",
                List.of("用户名", "昵称", "邮箱", "手机号", "单位", "岗位", "角色", "状态"),
                rows.stream()
                        .map(
                                u -> {
                                    UserVO vo = toUserVO(u);
                                    String roles =
                                            vo.getEffectiveRoleList() == null
                                                            || vo.getEffectiveRoleList().isEmpty()
                                                    ? ""
                                                    : vo.getEffectiveRoleList().stream()
                                                            .map(r -> r.getName())
                                                            .collect(Collectors.joining(","));
                                    return List.of(
                                            nullToEmpty(u.getUsername()),
                                            nullToEmpty(u.getNickname()),
                                            nullToEmpty(vo.getEmail()),
                                            nullToEmpty(vo.getPhone()),
                                            nullToEmpty(vo.getUnitName()),
                                            nullToEmpty(vo.getPostName()),
                                            roles,
                                            u.getStatus() != null && u.getStatus() == 1
                                                    ? "启用"
                                                    : "停用");
                                })
                        .toList());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isSeedAccount(String username) {
        return "SuperAdmin".equalsIgnoreCase(username) || "admin".equalsIgnoreCase(username);
    }

    private User findUserWithRoles(Long id) {
        return userRepository
                .findByIdWithRoles(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    private UserVO toUserVO(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            userRepository
                    .findByIdWithRoles(user.getId())
                    .ifPresent(
                            u -> {
                                user.setRoles(u.getRoles());
                                user.setUnit(u.getUnit());
                                user.setPost(u.getPost());
                            });
        } else if (user.getUnit() == null || user.getPost() == null) {
            userRepository
                    .findByIdWithRoles(user.getId())
                    .ifPresent(
                            u -> {
                                if (user.getUnit() == null) {
                                    user.setUnit(u.getUnit());
                                }
                                if (user.getPost() == null) {
                                    user.setPost(u.getPost());
                                }
                            });
        }
        UserVO vo = UserVO.from(user);
        if (user.getUnit() != null) {
            unitRepository
                    .findByIdWithRoles(user.getUnit().getId())
                    .ifPresent(
                            unit -> {
                                List<Role> unitRoles =
                                        unit.getRoles() == null
                                                ? List.of()
                                                : unit.getRoles().stream()
                                                        .collect(Collectors.toList());
                                vo.fillUnitRoles(unitRoles);
                            });
        } else {
            vo.fillUnitRoles(List.of());
        }
        applySensitiveMask(vo);
        return vo;
    }

    /** 无 {@code user:sensitive:view} 时，按系统配置对勾选字段打码 */
    private void applySensitiveMask(UserVO vo) {
        if (rbacService.hasPermission("user:sensitive:view")) {
            return;
        }
        var cfg = appConfigService.resolveSensitiveData();
        if (cfg == null || Boolean.FALSE.equals(cfg.getEnabled())) {
            return;
        }
        var fields = cfg.getFields();
        if (fields == null || fields.isEmpty()) {
            return;
        }
        boolean masked = false;
        if (fields.contains("phone")) {
            vo.setPhone(SensitiveDataUtil.maskPhone(vo.getPhone()));
            masked = true;
        }
        if (fields.contains("email")) {
            vo.setEmail(SensitiveDataUtil.maskEmail(vo.getEmail()));
            masked = true;
        }
        if (masked) {
            vo.setSensitiveMasked(true);
        }
    }
}
