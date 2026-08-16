package com.smartadmin.security;

import com.smartadmin.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 未认证请求统一返回 ApiResponse JSON，并区分“被强制下线”与“登录态过期”。 */
@Component
@RequiredArgsConstructor
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    /** JwtAuthFilter 命中黑名单时置位，供本入口点选择提示文案。 */
    public static final String REVOKED_ATTRIBUTE = "xn.auth.revoked";

    private static final String REVOKED_MESSAGE = "您已被管理员强制下线，请重新登录";
    private static final String EXPIRED_MESSAGE = "登录状态已失效，请重新登录";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        boolean revoked = Boolean.TRUE.equals(request.getAttribute(REVOKED_ATTRIBUTE));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write(
                        objectMapper.writeValueAsString(
                                ApiResponse.error(
                                        401, revoked ? REVOKED_MESSAGE : EXPIRED_MESSAGE)));
    }
}
