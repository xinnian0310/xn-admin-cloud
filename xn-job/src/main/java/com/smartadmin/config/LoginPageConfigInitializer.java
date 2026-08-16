package com.smartadmin.config;

import com.smartadmin.entity.SysLoginPageConfig;
import com.smartadmin.repository.SysLoginPageConfigRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 按当前前端登录页默认表现填充「登录页设置」数据。
 *
 * <p>登录页布局由前端固定；本配置仅管理验证码开关与类型。
 */
@Component
@Order(7)
@RequiredArgsConstructor
public class LoginPageConfigInitializer implements CommandLineRunner {

    /** 与前端 appConfig.app.name 对齐 */
    public static final String APP_NAME = "心念后台管理系统";

    private static final String NAME_CURRENT = "心念后台管理系统-当前登录页";
    private static final String NAME_SECURE = "心念后台管理系统-安全登录";

    private final SysLoginPageConfigRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        // Hibernate ddl-auto 不会把已有 NOT NULL 改成可空，启动时兼容修正
        relaxNullableColumns();
        if (repository.count() > 0) {
            return;
        }

        SysLoginPageConfig current = new SysLoginPageConfig();
        current.setName(NAME_CURRENT);
        current.setBackgroundUrl(null);
        current.setBackgroundFit("COVER");
        current.setBoxX(null);
        current.setBoxY(null);
        current.setCaptchaEnabled(false);
        current.setCaptchaType(null);
        current.setRemark("与当前登录页一致：不开启验证（标题：" + APP_NAME + "）");
        current.setStatus(1);
        repository.save(current);

        SysLoginPageConfig secure = new SysLoginPageConfig();
        secure.setName(NAME_SECURE);
        secure.setBackgroundUrl(null);
        secure.setBackgroundFit("COVER");
        secure.setBoxX(null);
        secure.setBoxY(null);
        secure.setCaptchaEnabled(true);
        secure.setCaptchaType("IMAGE");
        secure.setRemark("备选方案：开启图形验证码（默认未启用）");
        secure.setStatus(0);
        repository.save(secure);
    }

    private void relaxNullableColumns() {
        // 历史表可能是 boxx/boxy（无下划线）或 box_x/box_y
        for (String col : List.of("box_x", "boxx")) {
            if (columnExists(col)) {
                jdbcTemplate.execute(
                        "ALTER TABLE sys_login_page_config MODIFY COLUMN `"
                                + col
                                + "` DOUBLE NULL");
            }
        }
        for (String col : List.of("box_y", "boxy")) {
            if (columnExists(col)) {
                jdbcTemplate.execute(
                        "ALTER TABLE sys_login_page_config MODIFY COLUMN `"
                                + col
                                + "` DOUBLE NULL");
            }
        }
    }

    private boolean columnExists(String column) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_page_config' AND COLUMN_NAME = ?",
                        Integer.class,
                        column);
        return count != null && count > 0;
    }
}
