package com.smartadmin.config;

import com.smartadmin.entity.SysPageUiConfig;
import com.smartadmin.repository.SysPageUiConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(3)
@RequiredArgsConstructor
public class PageUiInitializer implements CommandLineRunner {

    private final SysPageUiConfigRepository pageUiConfigRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (pageUiConfigRepository.count() == 0) {
            saveRolesConfig();
            saveUsersConfig();
        }
        ensureRoutesConfig();
        ensureNoticesConfig();
        ensureUnitsConfig();
        ensureUsersSearchHasRole();
        ensureDictsConfig();
        ensureDictDataConfig();
        ensureLoginSettingsConfig();
        ensureLoginLogConfig();
        ensureOperLogConfig();
        ensureExceptionLogConfig();
        ensureMessagesConfig();
        ensureJobsConfig();
        ensureJobLogsConfig();
        ensurePostsConfig();
        ensureRecycleConfig();
        ensureFilesConfig();
        ensureOnlineConfig();
        ensureRedisConfig();
        ensureSqlConfig();
        ensureMineMessagesConfig();
        ensureSecurityConfig();
        ensurePermissionContentConfig();
    }

    private void ensureOnlineConfig() {
        if (pageUiConfigRepository.findByRoutePath("/monitor/online").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/monitor/online");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索用户名/昵称/IP"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensureRedisConfig() {
        if (pageUiConfigRepository.findByRoutePath("/monitor/redis").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/monitor/redis");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索 Key"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensureSqlConfig() {
        if (pageUiConfigRepository.findByRoutePath("/monitor/sql").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/monitor/sql");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索 SQL"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensureMineMessagesConfig() {
        if (pageUiConfigRepository.findByRoutePath("/messages/mine").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/messages/mine");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索标题/发送人"},
                  {"label":"状态","prop":"read","type":"select","placeholder":"请选择状态","options":[{"label":"未读","value":false},{"label":"已读","value":true}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensureSecurityConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/security").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/security");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索用户名"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensurePermissionContentConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/permissions-content").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/permissions-content");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索名称/编码"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    /** 文件管理页搜索配置 */
    private void ensureFilesConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/files").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/files");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索当前目录"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    /** 登录页设置搜索配置 */
    private void ensureLoginSettingsConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/login-settings").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/login-settings");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索配置名称"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"启用","value":1},{"label":"未启用","value":0}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    /** 字典类型管理页搜索配置 */
    private void ensureDictsConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/dicts").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/dicts");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索字典名称/编码"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"启用","value":1},{"label":"禁用","value":0}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    /** 字典数据管理页搜索配置 */
    private void ensureDictDataConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/dicts/data").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/dicts/data");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索字典标签/键值"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"启用","value":1},{"label":"禁用","value":0}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    /** 登录日志页搜索配置 */
    private void ensureLoginLogConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/logs/login").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/logs/login");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索用户名"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"成功","value":1},{"label":"失败","value":0}]},
                  {"label":"登录时间","prop":"loginTime","type":"daterange"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    /** 操作日志页搜索配置 */
    private void ensureOperLogConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/logs/oper").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/logs/oper");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索模块标题/操作人"},
                  {"label":"业务类型","prop":"businessType","type":"select","placeholder":"请选择业务类型","options":[{"label":"新增","value":"INSERT"},{"label":"修改","value":"UPDATE"},{"label":"删除","value":"DELETE"},{"label":"授权","value":"GRANT"},{"label":"导入","value":"IMPORT"},{"label":"导出","value":"EXPORT"},{"label":"清空","value":"CLEAN"},{"label":"其他","value":"OTHER"}]},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"成功","value":1},{"label":"失败","value":0}]},
                  {"label":"操作时间","prop":"operTime","type":"daterange"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    /** 单位管理页搜索配置 */
    private void ensureUnitsConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/units").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/units");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索单位名称/编码"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"启用","value":1},{"label":"禁用","value":0}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    /** 用户管理搜索区补充角色下拉（左侧树改为单位树） */
    private void ensureUsersSearchHasRole() {
        pageUiConfigRepository.findByRoutePath("/users").ifPresent(config -> {
            String search = config.getSearchConfig();
            if (search != null && search.contains("\"roleId\"")) {
                return;
            }
            config.setSearchConfig("""
                    [
                      {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索用户名/昵称/邮箱"},
                      {"label":"角色","prop":"roleId","type":"select","placeholder":"请选择角色","options":[]}
                    ]
                    """);
            pageUiConfigRepository.save(config);
        });
    }

    /** 公告管理页搜索配置 */
    private void ensureNoticesConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/notices").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/notices");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索公告标题"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"草稿","value":"DRAFT"},{"label":"已下发","value":"PUBLISHED"},{"label":"已撤回","value":"REVOKED"}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensureExceptionLogConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/logs/exception").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/logs/exception");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索URL/异常/操作人"},
                  {"label":"发生时间","prop":"operTime","type":"daterange"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensureMessagesConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/messages").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/messages");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索消息标题"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"草稿","value":"DRAFT"},{"label":"已发送","value":"SENT"}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensureJobsConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/jobs").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/jobs");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索任务名称/标识"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"启用","value":1},{"label":"停用","value":0}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensureJobLogsConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/jobs/logs").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/jobs/logs");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索任务名称/标识"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"成功","value":"SUCCESS"},{"label":"失败","value":"FAIL"},{"label":"跳过","value":"SKIP"}]},
                  {"label":"执行时间","prop":"range","type":"daterange"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensurePostsConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/posts").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/posts");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索岗位名称/编码"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"启用","value":1},{"label":"停用","value":0}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void ensureRecycleConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/recycle").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/recycle");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索标题/摘要/操作人"},
                  {"label":"类型","prop":"bizType","type":"select","placeholder":"请选择类型","options":[{"label":"用户","value":"USER"},{"label":"文件","value":"FILE"}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    /** 路由管理页：搜索区 + 默认按钮（新增/编辑/查看/删除） */
    private void ensureRoutesConfig() {
        if (pageUiConfigRepository.findByRoutePath("/system/routes").isPresent()) {
            return;
        }
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/routes");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索标题/路径"},
                  {"label":"类型","prop":"type","type":"select","placeholder":"请选择类型","options":[{"label":"目录","value":"DIR"},{"label":"菜单","value":"MENU"}]},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"启用","value":1},{"label":"禁用","value":0}]},
                  {"label":"内置","prop":"builtIn","type":"select","placeholder":"请选择","options":[{"label":"是","value":true},{"label":"否","value":false}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void saveRolesConfig() {
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/system/roles");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索角色名称/编码"},
                  {"label":"角色名称","prop":"name","type":"input","placeholder":"请输入角色名称"},
                  {"label":"角色编码","prop":"code","type":"input","placeholder":"请输入角色编码"},
                  {"label":"状态","prop":"status","type":"select","placeholder":"请选择状态","options":[{"label":"启用","value":1},{"label":"禁用","value":0}]},
                  {"label":"类型","prop":"builtIn","type":"select","placeholder":"请选择类型","options":[{"label":"内置","value":true},{"label":"自定义","value":false}]}
                ]
                """);
        pageUiConfigRepository.save(config);
    }

    private void saveUsersConfig() {
        SysPageUiConfig config = new SysPageUiConfig();
        config.setRoutePath("/users");
        config.setBuiltIn(true);
        config.setSearchConfig("""
                [
                  {"label":"综合查询","prop":"FuzzyWord","type":"input","placeholder":"搜索用户名/昵称/邮箱"}
                ]
                """);
        pageUiConfigRepository.save(config);
    }
}
