package com.smartadmin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** JWT 签发与校验关键路径（无 Spring 容器）。 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil =
                new JwtUtil(
                        "XnAdminSecretKeyForJwtTokenGeneration2026MustBeLongEnough", 3_600_000L);
    }

    @Test
    void generateAndValidateToken() {
        String token = jwtUtil.generateToken(7L, "SuperAdmin", List.of("SUPER_ADMIN"));
        assertTrue(jwtUtil.validateToken(token));
        assertEquals("SuperAdmin", jwtUtil.getUsername(token));
        assertEquals(7L, jwtUtil.getUserId(token));
        assertEquals(List.of("SUPER_ADMIN"), jwtUtil.getRoles(token));
    }

    @Test
    void rejectTamperedToken() {
        String token = jwtUtil.generateToken(1L, "admin", List.of("ADMIN"));
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        // 篡改 payload 段，签名应校验失败
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];
        assertFalse(jwtUtil.validateToken(tampered));
        assertFalse(jwtUtil.validateToken("not-a-jwt"));
    }
}
