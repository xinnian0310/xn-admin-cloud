package com.smartadmin.aspect;

import com.smartadmin.common.OperLog;
import com.smartadmin.common.WebUtils;
import com.smartadmin.service.OperLogService;
import com.smartadmin.service.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 环绕采集标注了 {@link OperLog} 的方法：操作人/耗时/入参/结果状态。
 * 采集失败绝不影响被拦截业务方法的返回值或异常传播。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private static final Pattern SENSITIVE_FIELD_PATTERN =
            Pattern.compile("\"(password|oldPassword|newPassword)\"\\s*:\\s*\"[^\"]*\"");

    private final OperLogService operLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            saveLog(joinPoint, operLog, start, 1, null);
            return result;
        } catch (Throwable ex) {
            saveLog(joinPoint, operLog, start, 0, ex.getMessage());
            throw ex;
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, OperLog operLog, long start, int status, String errorMsg) {
        try {
            HttpServletRequest request = WebUtils.getCurrentRequest();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String methodName = method.getDeclaringClass().getName() + "#" + method.getName();
            String operatorName = safeCurrentUsername();
            String requestMethod = request != null ? request.getMethod() : null;
            String requestUrl = request != null ? request.getRequestURI() : null;
            String ip = request != null ? WebUtils.getClientIp(request) : null;
            String params = serializeArgs(joinPoint.getArgs());
            long costTime = System.currentTimeMillis() - start;
            operLogService.record(
                    operLog.title(), operLog.businessType(), operatorName,
                    requestMethod, requestUrl, methodName, ip, params, status, errorMsg, costTime);
        } catch (Exception e) {
            log.warn("采集操作日志失败：{}", e.getMessage());
        }
    }

    private String safeCurrentUsername() {
        try {
            return RbacService.currentUsername();
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object[] filtered = Arrays.stream(args)
                .filter(arg -> !(arg instanceof HttpServletRequest)
                        && !(arg instanceof HttpServletResponse)
                        && !(arg instanceof MultipartFile))
                .toArray();
        if (filtered.length == 0) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(filtered);
            return SENSITIVE_FIELD_PATTERN.matcher(json).replaceAll("\"$1\":\"***\"");
        } catch (Exception e) {
            return null;
        }
    }
}
