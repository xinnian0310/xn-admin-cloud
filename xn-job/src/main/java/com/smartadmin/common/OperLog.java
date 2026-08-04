package com.smartadmin.common;

import com.smartadmin.entity.OperBusinessType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标注在 Controller 写操作方法上，由 OperLogAspect 环绕采集操作日志 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 模块标题，如“用户管理” */
    String title();

    /** 业务类型，用于日志分类展示 */
    OperBusinessType businessType() default OperBusinessType.OTHER;
}
