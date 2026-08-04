package com.smartadmin.service;

import com.smartadmin.dto.PageUiButtonItemDTO;
import com.smartadmin.dto.PageUiConfigVO;
import com.smartadmin.dto.PageUiSearchItemDTO;
import com.smartadmin.entity.Permission;
import com.smartadmin.entity.PermissionType;
import com.smartadmin.entity.SysRoute;
import com.smartadmin.repository.PermissionRepository;
import com.smartadmin.repository.SysPageUiConfigRepository;
import com.smartadmin.repository.SysRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PageUiService {

    private final SysPageUiConfigRepository pageUiConfigRepository;
    private final SysRouteRepository sysRouteRepository;
    private final PermissionRepository permissionRepository;
    private final RbacService rbacService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageUiConfigVO getConfigForCurrentUser(String routePath) {
        PageUiConfigVO vo = new PageUiConfigVO();
        vo.setRoutePath(routePath);

        // 搜索项属于纯 UI 配置，仍来自 sys_page_ui_config
        pageUiConfigRepository.findByRoutePath(routePath).ifPresent(config ->
                vo.setSearchItems(filterSearchItems(parseSearchConfig(config.getSearchConfig()))));

        // 按钮统一来自「权限内容」：
        // - BUTTON → 工具栏 xnButton（新增/编辑/查看/删除）
        // - TABLE_BUTTON → 表格操作列 xnTableActions（查看/编辑/删除 + 页面扩展）
        // - action=capability 仅作角色分配，不渲染到页面按钮
        Permission menu = resolveMenuPermission(routePath);
        if (menu != null) {
            vo.setButtons(collectButtons(menu, PermissionType.BUTTON));
            vo.setTableButtons(collectButtons(menu, PermissionType.TABLE_BUTTON));
        }
        return vo;
    }

    /** 根据路由 path 找到其绑定的菜单权限 */
    private Permission resolveMenuPermission(String routePath) {
        SysRoute route = sysRouteRepository.findByPath(routePath).orElse(null);
        if (route == null || !StringUtils.hasText(route.getPermission())) {
            return null;
        }
        return permissionRepository.findByCode(route.getPermission()).orElse(null);
    }

    /** 取菜单下指定类型的按钮子权限，按当前用户已分配权限过滤并按 sort 排序 */
    private List<PageUiButtonItemDTO> collectButtons(Permission menu, PermissionType type) {
        List<PageUiButtonItemDTO> result = new ArrayList<>();
        if (menu.getChildren() == null) {
            return result;
        }
        menu.getChildren().stream()
                .filter(child -> child.getType() == type)
                .filter(child -> !"capability".equals(child.getAction()))
                .filter(child -> rbacService.hasPermission(child.getCode()))
                .sorted(Comparator.comparing(p -> p.getSort() == null ? 0 : p.getSort()))
                .forEach(child -> result.add(toButtonDto(child)));
        return result;
    }

    private PageUiButtonItemDTO toButtonDto(Permission permission) {
        PageUiButtonItemDTO dto = new PageUiButtonItemDTO();
        dto.setName(permission.getName());
        dto.setAction(permission.getAction());
        dto.setType("button");
        // 表格按钮默认不带图标；工具栏按钮保留图标
        if (permission.getType() != PermissionType.TABLE_BUTTON) {
            dto.setIcon(permission.getIcon());
        }
        dto.setTypeColor(permission.getButtonColor());
        dto.setPermission(permission.getCode());
        // 编辑 / 查看 / 下线 必须单选；删除等批量操作不设 index
        String action = permission.getAction();
        if ("edit".equals(action) || "view".equals(action) || "offline".equals(action)) {
            dto.setIndex(0);
        }
        return dto;
    }

    private List<PageUiSearchItemDTO> parseSearchConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PageUiSearchItemDTO>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<PageUiSearchItemDTO> filterSearchItems(List<PageUiSearchItemDTO> items) {
        List<PageUiSearchItemDTO> result = new ArrayList<>();
        for (PageUiSearchItemDTO item : items) {
            if (!StringUtils.hasText(item.getPermission()) || rbacService.hasPermission(item.getPermission())) {
                result.add(item);
            }
        }
        return result;
    }
}
