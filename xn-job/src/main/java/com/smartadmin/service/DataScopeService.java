package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.entity.DataScope;
import com.smartadmin.entity.Role;
import com.smartadmin.entity.SysUnit;
import com.smartadmin.entity.User;
import com.smartadmin.repository.SysUnitRepository;
import com.smartadmin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 解析当前用户的数据权限范围，供业务列表/详情强制过滤。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final RbacService rbacService;
    private final SysUnitRepository unitRepository;
    private final UserRepository userRepository;

    /**
     * 按「创建人/发布人」过滤：ALL 不过滤；SELF 仅本人；UNIT* 仅单位内用户。
     */
    public record OwnerFilter(boolean unrestricted, boolean none, Long selfUserId, List<Long> ownerIds) {
        public static OwnerFilter ofUnrestricted() {
            return new OwnerFilter(true, false, null, List.of());
        }

        public static OwnerFilter ofNone() {
            return new OwnerFilter(false, true, null, List.of(-1L));
        }

        public static OwnerFilter ofSelf(Long selfUserId) {
            if (selfUserId == null) {
                return ofNone();
            }
            return new OwnerFilter(false, false, selfUserId, List.of(selfUserId));
        }

        public static OwnerFilter ofOwners(List<Long> ownerIds) {
            if (ownerIds == null || ownerIds.isEmpty()) {
                return ofNone();
            }
            return new OwnerFilter(false, false, null, ownerIds);
        }
    }

    /** 按用户名过滤（登录/操作/异常日志、文件上传人）。 */
    public record UsernameFilter(boolean unrestricted, boolean none, List<String> usernames) {
        public static UsernameFilter ofUnrestricted() {
            return new UsernameFilter(true, false, List.of());
        }

        public static UsernameFilter ofNone() {
            return new UsernameFilter(false, true, List.of("__none__"));
        }

        public static UsernameFilter of(List<String> usernames) {
            if (usernames == null || usernames.isEmpty()) {
                return ofNone();
            }
            return new UsernameFilter(false, false, usernames);
        }
    }

    public record ResolvedScope(
            DataScope scope,
            boolean all,
            boolean selfOnly,
            Long selfUserId,
            List<Long> unitIds
    ) {
        public static ResolvedScope ofAll(Long selfUserId) {
            return new ResolvedScope(DataScope.ALL, true, false, selfUserId, List.of());
        }

        public static ResolvedScope ofSelf(Long selfUserId) {
            return new ResolvedScope(DataScope.SELF, false, true, selfUserId, List.of());
        }

        public static ResolvedScope ofUnits(DataScope scope, Long selfUserId, List<Long> unitIds) {
            return new ResolvedScope(scope, false, false, selfUserId, unitIds == null ? List.of() : unitIds);
        }
    }

    public record UnitFilter(boolean unrestricted, boolean selfOnly, boolean none, List<Long> unitIds) {
        public static UnitFilter ofUnrestricted() {
            return new UnitFilter(true, false, false, List.of());
        }

        public static UnitFilter ofSelfOnly() {
            return new UnitFilter(false, true, false, List.of());
        }

        public static UnitFilter ofNone() {
            return new UnitFilter(false, false, true, List.of(-1L));
        }

        public static UnitFilter of(List<Long> unitIds) {
            if (unitIds == null || unitIds.isEmpty()) {
                return ofNone();
            }
            return new UnitFilter(false, false, false, unitIds);
        }
    }

    public ResolvedScope resolveCurrent() {
        return resolve(rbacService.currentUser());
    }

    public ResolvedScope resolve(User user) {
        if (user == null) {
            return ResolvedScope.ofSelf(null);
        }
        if (rbacService.isSuperAdmin(user)) {
            return ResolvedScope.ofAll(user.getId());
        }

        DataScope widest = resolveWidestScope(user);
        Long unitId = user.getUnit() != null ? user.getUnit().getId() : null;

        return switch (widest) {
            case ALL -> ResolvedScope.ofAll(user.getId());
            case SELF -> ResolvedScope.ofSelf(user.getId());
            case UNIT -> {
                if (unitId == null) {
                    yield ResolvedScope.ofSelf(user.getId());
                }
                yield ResolvedScope.ofUnits(DataScope.UNIT, user.getId(), List.of(unitId));
            }
            case UNIT_AND_CHILDREN -> {
                if (unitId == null) {
                    yield ResolvedScope.ofSelf(user.getId());
                }
                yield ResolvedScope.ofUnits(
                        DataScope.UNIT_AND_CHILDREN,
                        user.getId(),
                        collectSelfAndDescendantIds(unitId));
            }
        };
    }

    public UnitFilter applyUnitFilter(ResolvedScope scope, Long uiUnitId) {
        if (scope.all()) {
            if (uiUnitId == null) {
                return UnitFilter.ofUnrestricted();
            }
            return UnitFilter.of(collectSelfAndDescendantIds(uiUnitId));
        }
        if (scope.selfOnly()) {
            return UnitFilter.ofSelfOnly();
        }
        List<Long> allowed = scope.unitIds();
        if (allowed.isEmpty()) {
            return UnitFilter.ofNone();
        }
        if (uiUnitId == null) {
            return UnitFilter.of(allowed);
        }
        List<Long> uiIds = collectSelfAndDescendantIds(uiUnitId);
        List<Long> intersect = uiIds.stream().filter(allowed::contains).toList();
        if (intersect.isEmpty()) {
            return UnitFilter.ofNone();
        }
        return UnitFilter.of(intersect);
    }

    public boolean isUserAccessible(User target) {
        if (target == null) {
            return false;
        }
        ResolvedScope scope = resolveCurrent();
        if (scope.all()) {
            return true;
        }
        if (scope.selfOnly()) {
            return Objects.equals(scope.selfUserId(), target.getId());
        }
        Long targetUnitId = target.getUnit() != null ? target.getUnit().getId() : null;
        return targetUnitId != null && scope.unitIds().contains(targetUnitId);
    }

    public void assertUserAccessible(User target) {
        if (!isUserAccessible(target)) {
            throw new BusinessException(403, "无数据权限");
        }
    }

    public boolean isUnitAccessible(Long unitId) {
        if (unitId == null) {
            return false;
        }
        ResolvedScope scope = resolveCurrent();
        if (scope.all()) {
            return true;
        }
        if (scope.selfOnly()) {
            User me = rbacService.currentUser();
            return me.getUnit() != null && Objects.equals(me.getUnit().getId(), unitId);
        }
        return scope.unitIds().contains(unitId);
    }

    public void assertUnitAccessible(Long unitId) {
        if (!isUnitAccessible(unitId)) {
            throw new BusinessException(403, "无数据权限");
        }
    }

    public OwnerFilter resolveOwnerFilter() {
        ResolvedScope scope = resolveCurrent();
        if (scope.all()) {
            return OwnerFilter.ofUnrestricted();
        }
        if (scope.selfOnly()) {
            return OwnerFilter.ofSelf(scope.selfUserId());
        }
        List<Long> unitIds = scope.unitIds();
        if (unitIds.isEmpty()) {
            return OwnerFilter.ofNone();
        }
        List<Long> ownerIds = new ArrayList<>(userRepository.findIdsByUnitIdIn(unitIds));
        if (scope.selfUserId() != null && !ownerIds.contains(scope.selfUserId())) {
            ownerIds.add(scope.selfUserId());
        }
        return OwnerFilter.ofOwners(ownerIds);
    }

    public UsernameFilter resolveUsernameFilter() {
        ResolvedScope scope = resolveCurrent();
        if (scope.all()) {
            return UsernameFilter.ofUnrestricted();
        }
        if (scope.selfOnly()) {
            User me = rbacService.currentUser();
            if (me == null || !StringUtils.hasText(me.getUsername())) {
                return UsernameFilter.ofNone();
            }
            return UsernameFilter.of(List.of(me.getUsername()));
        }
        List<Long> unitIds = scope.unitIds();
        if (unitIds.isEmpty()) {
            return UsernameFilter.ofNone();
        }
        List<String> names = new ArrayList<>(userRepository.findUsernamesByUnitIdIn(unitIds));
        User me = rbacService.currentUser();
        if (me != null && StringUtils.hasText(me.getUsername()) && !names.contains(me.getUsername())) {
            names.add(me.getUsername());
        }
        return UsernameFilter.of(names);
    }

    public void assertOwnerAccessible(Long ownerId) {
        OwnerFilter filter = resolveOwnerFilter();
        if (filter.unrestricted()) {
            return;
        }
        if (filter.none() || ownerId == null || !filter.ownerIds().contains(ownerId)) {
            throw new BusinessException(403, "无数据权限");
        }
    }

    public void assertUsernameAccessible(String username) {
        UsernameFilter filter = resolveUsernameFilter();
        if (filter.unrestricted()) {
            return;
        }
        if (filter.none() || !StringUtils.hasText(username) || !filter.usernames().contains(username)) {
            throw new BusinessException(403, "无数据权限");
        }
    }

    /**
     * 当前数据权限范围内、状态启用的用户（公告下发 / 站内信全员发送等）。
     */
    public List<User> listAccessibleActiveUsers() {
        List<User> active = userRepository.findByStatus(1);
        ResolvedScope scope = resolveCurrent();
        if (scope.all()) {
            return active;
        }
        return active.stream().filter(this::isUserAccessible).toList();
    }

    /** 过滤出当前用户有数据权限的用户列表。 */
    public List<User> filterAccessibleUsers(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        ResolvedScope scope = resolveCurrent();
        if (scope.all()) {
            return users;
        }
        return users.stream().filter(this::isUserAccessible).toList();
    }

    public List<SysUnit> filterUnits(List<SysUnit> all) {
        ResolvedScope scope = resolveCurrent();
        if (scope.all()) {
            return all;
        }
        Set<Long> allowed = new HashSet<>();
        if (scope.selfOnly()) {
            User me = rbacService.currentUser();
            if (me.getUnit() != null) {
                allowed.add(me.getUnit().getId());
            }
        } else {
            allowed.addAll(scope.unitIds());
        }
        if (allowed.isEmpty()) {
            return List.of();
        }
        Set<Long> keep = new HashSet<>(allowed);
        for (Long id : allowed) {
            Long cursor = id;
            while (cursor != null) {
                keep.add(cursor);
                Long parentId = null;
                for (SysUnit u : all) {
                    if (Objects.equals(u.getId(), cursor)) {
                        parentId = u.getParentId();
                        break;
                    }
                }
                cursor = parentId;
            }
        }
        return all.stream().filter(u -> keep.contains(u.getId())).toList();
    }

    /** 自身 + 全部子孙单位 id */
    public List<Long> collectSelfAndDescendantIds(Long unitId) {
        if (unitId == null) {
            return List.of();
        }
        List<SysUnit> all = unitRepository.findAllByOrderBySortAscIdAsc();
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (SysUnit unit : all) {
            if (unit.getParentId() == null) {
                continue;
            }
            childrenMap.computeIfAbsent(unit.getParentId(), k -> new ArrayList<>()).add(unit.getId());
        }
        List<Long> result = new ArrayList<>();
        collectIds(unitId, childrenMap, result);
        return result;
    }

    private void collectIds(Long id, Map<Long, List<Long>> childrenMap, List<Long> result) {
        result.add(id);
        for (Long child : childrenMap.getOrDefault(id, List.of())) {
            collectIds(child, childrenMap, result);
        }
    }

    private DataScope resolveWidestScope(User user) {
        Set<Role> roles = new HashSet<>();
        if (user.getRoles() != null) {
            roles.addAll(user.getRoles());
        }
        if (user.getUnit() != null && user.getUnit().getId() != null) {
            unitRepository.findByIdWithRoles(user.getUnit().getId()).ifPresent(unit -> {
                if (unit.getRoles() != null) {
                    roles.addAll(unit.getRoles());
                }
            });
        }
        if (roles.isEmpty()) {
            return DataScope.UNIT_AND_CHILDREN;
        }
        return roles.stream()
                .map(r -> r.getDataScope() != null ? r.getDataScope() : DataScope.UNIT_AND_CHILDREN)
                .max(Comparator.comparingInt(this::scopeRank))
                .orElse(DataScope.UNIT_AND_CHILDREN);
    }

    private int scopeRank(DataScope scope) {
        return switch (scope) {
            case ALL -> 4;
            case UNIT_AND_CHILDREN -> 3;
            case UNIT -> 2;
            case SELF -> 1;
        };
    }
}
