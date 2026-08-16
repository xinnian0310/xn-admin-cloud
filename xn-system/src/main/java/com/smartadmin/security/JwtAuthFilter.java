package com.smartadmin.security;

import com.smartadmin.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            Long userId = jwtUtil.getUserId(token);
            long iat = jwtUtil.getIssuedAtMillis(token);
            if (tokenBlacklistService.isRevoked(token, userId, iat)) {
                request.setAttribute(RestAuthEntryPoint.REVOKED_ATTRIBUTE, Boolean.TRUE);
            } else {
                try {
                    UserDetails userDetails = resolveUserDetails(token, userId);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (UsernameNotFoundException ignored) {
                    // 令牌主体已失效（用户被删/改名后旧缓存等），按未登录继续，由接口鉴权处理
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private UserDetails resolveUserDetails(String token, Long userId) {
        if (userId != null) {
            try {
                return userDetailsService.loadUserById(userId);
            } catch (UsernameNotFoundException ignored) {
                // 回退用户名（兼容旧令牌）
            }
        }
        return userDetailsService.loadUserByUsername(jwtUtil.getUsername(token));
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
