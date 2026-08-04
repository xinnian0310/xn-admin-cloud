package com.smartadmin.scheduler;

import org.quartz.CronScheduleBuilder;
import org.springframework.util.StringUtils;

/**
 * 对标若依 misfire 策略。
 * <ul>
 *   <li>0 - 默认（等同不触发立即执行）</li>
 *   <li>1 - 忽略 misfire，尽快补齐</li>
 *   <li>2 - 触发一次（FireAndProceed）</li>
 *   <li>3 - 不触发立即执行（DoNothing）</li>
 * </ul>
 */
public final class JobMisfirePolicy {

    public static final String DEFAULT = "0";
    public static final String IGNORE_MISFIRES = "1";
    public static final String FIRE_AND_PROCEED = "2";
    public static final String DO_NOTHING = "3";

    private JobMisfirePolicy() {
    }

    public static String normalize(String policy) {
        if (!StringUtils.hasText(policy)) {
            return DEFAULT;
        }
        return switch (policy.trim()) {
            case IGNORE_MISFIRES, FIRE_AND_PROCEED, DO_NOTHING, DEFAULT -> policy.trim();
            default -> DEFAULT;
        };
    }

    public static CronScheduleBuilder apply(CronScheduleBuilder builder, String policy) {
        return switch (normalize(policy)) {
            case IGNORE_MISFIRES -> builder.withMisfireHandlingInstructionIgnoreMisfires();
            case FIRE_AND_PROCEED -> builder.withMisfireHandlingInstructionFireAndProceed();
            default -> builder.withMisfireHandlingInstructionDoNothing();
        };
    }

    public static String label(String policy) {
        return switch (normalize(policy)) {
            case IGNORE_MISFIRES -> "忽略 misfire";
            case FIRE_AND_PROCEED -> "立即补偿执行";
            case DO_NOTHING -> "放弃本次（不触发）";
            default -> "默认（放弃本次）";
        };
    }
}
