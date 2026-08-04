package com.smartadmin.service;

import com.smartadmin.config.InfraProperties;
import com.smartadmin.config.KkFileViewProperties;
import com.smartadmin.config.MinioProperties;
import com.smartadmin.config.AppNacosProperties;
import com.smartadmin.config.RedisProperties;
import com.smartadmin.config.nacos.NacosConfigRefreshService;
import com.smartadmin.dto.InfraStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

@Service
@RequiredArgsConstructor
public class InfraStatusService {

    private final RedisProperties redisProperties;
    private final MinioProperties minioProperties;
    private final AppNacosProperties AppNacosProperties;
    private final KkFileViewProperties kkFileViewProperties;
    private final InfraProperties infraProperties;
    private final RedisMonitorService redisMonitorService;
    private final MinioStorageService minioStorageService;
    private final InfraRestartService infraRestartService;
    private final NacosConfigRefreshService nacosConfigRefreshService;

    @Value("${server.port:8080}")
    private int serverPort;

    public InfraStatusVO status() {
        InfraStatusVO vo = new InfraStatusVO();
        var tip = infraRestartService.tipMeta();
        vo.setRestartEnabled(Boolean.TRUE.equals(tip.get("restartEnabled")));
        vo.setProjectRoot(String.valueOf(tip.get("projectRoot")));
        vo.setStartCommand(String.valueOf(tip.get("startCommand")));

        InfraStatusVO.ComponentStatus redis = vo.getRedis();
        redis.setName("redis");
        redis.setRestartable(true);
        redis.setEnabled(redisProperties.isEnabled());
        redis.setEndpoint(redisProperties.getHost() + ":" + redisProperties.getPort());
        if (!redisProperties.isEnabled()) {
            redis.setStatus("DISABLED");
            redis.setMessage("未启用");
            redis.setRestartable(false);
        } else {
            var info = redisMonitorService.info();
            redis.setStatus(info.getStatus());
            redis.setMessage(info.getMessage());
        }

        InfraStatusVO.ComponentStatus minio = vo.getMinio();
        minio.setName("minio");
        minio.setRestartable(true);
        minio.setEnabled(minioProperties.isEnabled());
        minio.setEndpoint(minioProperties.getEndpoint());
        if (!minioProperties.isEnabled()) {
            minio.setStatus("DISABLED");
            minio.setMessage("未启用，文件使用本地 uploads");
            minio.setRestartable(false);
        } else {
            String ping = minioStorageService.pingMessage();
            minio.setStatus(ping.startsWith("UP") ? "UP" : ping.startsWith("DISABLED") ? "DISABLED" : "DOWN");
            minio.setMessage(ping);
        }

        InfraStatusVO.ComponentStatus nacos = vo.getNacos();
        nacos.setName("nacos");
        nacos.setRestartable(true);
        nacos.setEnabled(AppNacosProperties.isEnabled());
        nacos.setEndpoint(AppNacosProperties.getServerAddr());
        if (!AppNacosProperties.isEnabled()) {
            nacos.setStatus("DISABLED");
            nacos.setMessage("未启用");
            nacos.setRestartable(false);
        } else {
            fillHttp(nacos, "http://" + AppNacosProperties.getServerAddr() + "/nacos/");
            String configMsg = nacosConfigRefreshService.getLastMessage();
            if (configMsg != null && !configMsg.isBlank()) {
                String base = nacos.getMessage() == null ? "" : nacos.getMessage();
                nacos.setMessage(base + (base.isBlank() ? "" : " | ") + "配置中心: " + configMsg);
            }
        }

        InfraStatusVO.ComponentStatus kk = vo.getKkfileview();
        kk.setName("kkfileview");
        kk.setRestartable(true);
        kk.setEnabled(kkFileViewProperties.isEnabled());
        kk.setEndpoint(kkFileViewProperties.getBaseUrl());
        if (!kkFileViewProperties.isEnabled()) {
            kk.setStatus("DISABLED");
            kk.setMessage("未启用");
            kk.setRestartable(false);
        } else {
            fillHttp(kk, kkFileViewProperties.getBaseUrl());
        }

        InfraStatusVO.ComponentStatus backend = vo.getBackend();
        backend.setName("backend");
        backend.setEnabled(true);
        backend.setRestartable(false);
        backend.setStatus("UP");
        backend.setEndpoint("http://127.0.0.1:" + serverPort);
        backend.setMessage("本服务运行中");

        if (!infraProperties.isRestartEnabled()) {
            redis.setRestartable(false);
            minio.setRestartable(false);
            nacos.setRestartable(false);
            kk.setRestartable(false);
        }

        return vo;
    }

    private void fillHttp(InfraStatusVO.ComponentStatus c, String url) {
        try {
            URL u = URI.create(url.endsWith("/") ? url : url + "/").toURL();
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            c.setStatus(code >= 200 && code < 500 ? "UP" : "DOWN");
            c.setMessage("HTTP " + code);
            conn.disconnect();
        } catch (Exception e) {
            c.setStatus("DOWN");
            c.setMessage(e.getMessage());
        }
    }
}
