package com.smartadmin.config;

import com.smartadmin.security.JwtAuthFilter;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final SecurityProperties securityProperties;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> {
                            auth.requestMatchers("/api/auth/login")
                                    .permitAll()
                                    .requestMatchers("/api/auth/logout")
                                    .permitAll()
                                    .requestMatchers("/api/auth/captcha", "/api/auth/captcha/**")
                                    .permitAll()
                                    .requestMatchers("/api/login-page-configs/active")
                                    .permitAll()
                                    .requestMatchers("/api/system-config/public")
                                    .permitAll()
                                    .requestMatchers("/uploads/**")
                                    .permitAll()
                                    .requestMatchers("/ws/**")
                                    .permitAll()
                                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                                    .permitAll();
                            if (securityProperties.getSwagger().isEnabled()) {
                                auth.requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll();
                            }
                            // Actuator：健康检查可匿名（编排探活）；prometheus 需认证
                            auth.requestMatchers(
                                            "/actuator/health",
                                            "/actuator/health/**",
                                            "/actuator/info")
                                    .permitAll();
                            auth.anyRequest().authenticated();
                        })
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = securityProperties.getCors().allowedOriginList();
        boolean wildcard = origins.isEmpty() || origins.stream().anyMatch(o -> "*".equals(o));
        boolean prod =
                Arrays.stream(environment.getActiveProfiles())
                        .anyMatch(p -> "prod".equalsIgnoreCase(p));

        if (wildcard) {
            // * 与 allowCredentials 不能同时开启；开发用 pattern，生产拒绝裸 *
            if (prod) {
                throw new IllegalStateException(
                        "生产环境禁止 CORS 使用 *，请配置 app.security.cors.allowed-origins 白名单");
            }
            configuration.setAllowedOriginPatterns(List.of("*"));
            configuration.setAllowCredentials(false);
        } else {
            configuration.setAllowedOrigins(origins);
            configuration.setAllowCredentials(true);
        }
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
