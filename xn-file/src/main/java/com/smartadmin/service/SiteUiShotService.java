package com.smartadmin.service;

import com.smartadmin.dto.SiteUiShotVO;
import com.smartadmin.entity.RouteType;
import com.smartadmin.entity.SysRoute;
import com.smartadmin.repository.SysRouteRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 按路由管理菜单树排序，生成可展示的界面截图清单（公开接口，无需鉴权）。
 *
 * <p>仅输出已登记截图映射的 MENU；目录节点只参与排序遍历，不出现在结果中。登录页非菜单项，固定置顶。
 */
@Service
@RequiredArgsConstructor
public class SiteUiShotService {

    private static final String PROJECT_VUE = "vue";
    private static final String PROJECT_REACT = "react";

    /** 路由 path → 截图文件名（与 xn-home/public/docs/images/* 对齐） */
    private static final Map<String, String> PATH_IMAGE_MAP = buildPathImageMap();

    /** 各前端项目截图目录：xn-home/public/docs/images/{projectDir} */
    private static final Map<String, String> PROJECT_IMAGE_BASE =
            Map.of(
                    PROJECT_VUE, "/docs/images/xn-admin-vue3-ts",
                    PROJECT_REACT, "/docs/images/xn-admin-react-ts");

    private final SysRouteRepository routeRepository;
    private final AppCacheService appCacheService;

    public SiteUiShotVO getPublic(String project) {
        String key = normalizeProject(project);
        return appCacheService.getSiteUiShots(
                key, new tools.jackson.core.type.TypeReference<>() {}, () -> build(key));
    }

    private SiteUiShotVO build(String project) {
        SiteUiShotVO vo = new SiteUiShotVO();
        vo.setProject(project);
        vo.setImageBase(PROJECT_IMAGE_BASE.getOrDefault(project, "/docs/images/" + project));
        if (!PROJECT_VUE.equals(project) && !PROJECT_REACT.equals(project)) {
            return vo;
        }

        List<SiteUiShotVO.ShotItem> shots = new ArrayList<>();

        SiteUiShotVO.ShotItem login = new SiteUiShotVO.ShotItem();
        login.setSort(0);
        login.setTitle("登录页");
        login.setPath("/login");
        login.setImage("login.png");
        shots.add(login);

        List<SysRoute> all = routeRepository.findAllWithParent();
        Map<Long, List<SysRoute>> childrenMap = new HashMap<>();
        List<SysRoute> roots = new ArrayList<>();
        for (SysRoute route : all) {
            if (route.getParent() == null || route.getParent().getId() == null) {
                roots.add(route);
            } else {
                childrenMap
                        .computeIfAbsent(route.getParent().getId(), id -> new ArrayList<>())
                        .add(route);
            }
        }
        roots.sort(routeComparator());
        for (List<SysRoute> children : childrenMap.values()) {
            children.sort(routeComparator());
        }

        int[] counter = {1};
        for (SysRoute root : roots) {
            walk(root, childrenMap, shots, counter);
        }
        vo.setShots(shots);
        return vo;
    }

    private void walk(
            SysRoute route,
            Map<Long, List<SysRoute>> childrenMap,
            List<SiteUiShotVO.ShotItem> shots,
            int[] counter) {
        if (route == null || !Objects.equals(route.getStatus(), 1)) {
            return;
        }
        if (Boolean.TRUE.equals(route.getHidden())) {
            // 隐藏菜单不展示，但仍遍历其子节点（如字典数据）
            for (SysRoute child : childrenMap.getOrDefault(route.getId(), List.of())) {
                walk(child, childrenMap, shots, counter);
            }
            return;
        }

        if (route.getType() == RouteType.MENU) {
            String path = normalizePath(route.getPath());
            String image = PATH_IMAGE_MAP.get(path);
            if (StringUtils.hasText(image)) {
                SiteUiShotVO.ShotItem item = new SiteUiShotVO.ShotItem();
                item.setSort(counter[0]++);
                item.setTitle(StringUtils.hasText(route.getTitle()) ? route.getTitle() : path);
                item.setPath(path);
                item.setImage(image);
                shots.add(item);
            }
        }

        for (SysRoute child : childrenMap.getOrDefault(route.getId(), List.of())) {
            walk(child, childrenMap, shots, counter);
        }
    }

    private static Comparator<SysRoute> routeComparator() {
        return Comparator.comparing((SysRoute r) -> r.getSort() == null ? 0 : r.getSort())
                .thenComparing(r -> r.getId() == null ? 0L : r.getId());
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static String normalizeProject(String project) {
        if (!StringUtils.hasText(project)) {
            return PROJECT_VUE;
        }
        return project.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> buildPathImageMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("/dashboard", "dashboard.png");
        map.put("/profile", "profile.png");
        map.put("/messages/mine", "messages-mine.png");
        map.put("/monitor/online", "monitor-online.png");
        map.put("/monitor/server", "monitor-server.png");
        map.put("/monitor/redis", "monitor-redis.png");
        map.put("/monitor/sql", "monitor-sql.png");
        map.put("/system/logs/login", "logs-login.png");
        map.put("/system/logs/oper", "logs-oper.png");
        map.put("/system/logs/exception", "logs-exception.png");
        map.put("/users", "users.png");
        map.put("/system/units", "units.png");
        map.put("/system/posts", "posts.png");
        map.put("/system/roles", "roles.png");
        map.put("/system/permissions", "permissions.png");
        map.put("/system/permissions-content", "permissions-content.png");
        map.put("/system/routes", "routes.png");
        map.put("/system/notices", "notices.png");
        map.put("/system/messages", "messages.png");
        map.put("/system/dicts", "dicts.png");
        map.put("/system/login-settings", "login-settings.png");
        map.put("/system/config", "config.png");
        map.put("/system/security", "security.png");
        map.put("/system/remote-storage", "remote-storage.png");
        map.put("/system/site-contact", "site-contact.png");
        map.put("/system/files", "files.png");
        map.put("/system/jobs", "jobs.png");
        map.put("/system/jobs/logs", "jobs-log.png");
        map.put("/system/recycle", "recycle.png");
        map.put("/system/codegen", "codegen.png");
        map.put("/system/api-docs", "api-docs.png");
        return Map.copyOf(map);
    }
}
