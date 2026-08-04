package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.config.InfraProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InfraRestartService {

    private static final Set<String> ALLOWED = Set.of("redis", "minio", "nacos", "kkfileview");

    private final InfraProperties infraProperties;

    public Map<String, Object> restart(String rawName) {
        if (!infraProperties.isRestartEnabled()) {
            throw new BusinessException("基础设施一键重启已关闭（app.infra.restart-enabled=false）");
        }
        String name = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(name)) {
            throw new BusinessException("不支持重启组件：" + rawName + "，仅允许 redis/minio/nacos/kkfileview");
        }
        Path root = resolveProjectRoot();
        if (!Files.isRegularFile(root.resolve("启动.bat"))) {
            throw new BusinessException("未找到项目根 启动.bat：" + root);
        }

        List<Integer> ports = portsOf(name);
        for (int port : ports) {
            killPort(port);
        }
        if ("nacos".equals(name)) {
            Path lock = root.resolve("tool/nacos3/data/derby-data/db.lck");
            try {
                Files.deleteIfExists(lock);
            } catch (IOException e) {
                log.warn("删除 Nacos derby 锁失败: {}", e.getMessage());
            }
            try {
                Files.deleteIfExists(root.resolve("logs/nacos.log"));
            } catch (IOException ignored) {
                // ignore
            }
        }
        if ("kkfileview".equals(name)) {
            killKkResiduals();
        }

        sleepQuiet(800);
        startComponent(root, name);
        return Map.of("name", name, "projectRoot", root.toString(), "message", "已发送重启指令，请稍后刷新状态");
    }

    public Path resolveProjectRoot() {
        String configured = infraProperties.getProjectRoot();
        if (configured != null && !configured.isBlank()) {
            Path p = Path.of(configured.trim()).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        if (Files.isRegularFile(cwd.resolve("启动.bat"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("启动.bat"))) {
            return parent;
        }
        return cwd;
    }

    private List<Integer> portsOf(String name) {
        return switch (name) {
            case "redis" -> List.of(6379);
            case "minio" -> List.of(9000, 9001);
            case "nacos" -> List.of(8849, 8850);
            case "kkfileview" -> List.of(8012, 2001, 2002);
            default -> List.of();
        };
    }

    private void killPort(int port) {
        try {
            Process p =
                    new ProcessBuilder(
                                    "cmd.exe",
                                    "/c",
                                    "netstat -ano | findstr \":" + port + " \" | findstr LISTENING")
                            .redirectErrorStream(true)
                            .start();
            List<String> lines = readLines(p);
            p.waitFor(5, TimeUnit.SECONDS);
            for (String line : lines) {
                // netstat 行末 PID：取最后一个数字 token
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 5 && parts[parts.length - 1].matches("\\d+")) {
                    killPid(parts[parts.length - 1]);
                }
            }
        } catch (Exception e) {
            log.warn("释放端口 {} 失败: {}", port, e.getMessage());
        }
    }

    private void killPid(String pid) {
        if (pid == null || pid.isBlank() || "0".equals(pid)) {
            return;
        }
        try {
            new ProcessBuilder("taskkill", "/F", "/T", "/PID", pid)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(5, TimeUnit.SECONDS);
            log.info("已结束 PID {}", pid);
        } catch (Exception e) {
            log.warn("taskkill {} 失败: {}", pid, e.getMessage());
        }
    }

    private void killKkResiduals() {
        try {
            String ps =
                    """
                    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
                    Where-Object {
                      ($_.Name -match '^(java|javaw)\\.exe$' -and $_.CommandLine -match 'kkFileView') -or
                      (($_.Name -match '^(soffice(\\.bin)?|LibreOfficePortable)\\.exe$' -or $_.Name -eq 'soffice.bin') -and
                       (($_.CommandLine -and $_.CommandLine -match 'kkFileView') -or
                        ($_.ExecutablePath -and $_.ExecutablePath -match 'kkFileView')))
                    } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
                    """;
            new ProcessBuilder(
                            "powershell.exe",
                            "-NoProfile",
                            "-ExecutionPolicy",
                            "Bypass",
                            "-Command",
                            ps)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("清理 kkFileView 残留进程失败: {}", e.getMessage());
        }
    }

    private void startComponent(Path root, String name) {
        Path tool = root.resolve("tool");
        Path logs = root.resolve("logs");
        try {
            Files.createDirectories(logs);
        } catch (IOException e) {
            throw new BusinessException("无法创建日志目录: " + logs);
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null || javaHome.isBlank()) {
            Path jdk21 = Path.of("E:\\JDK\\JDK21");
            if (Files.isRegularFile(jdk21.resolve("bin/java.exe"))) {
                javaHome = jdk21.toString();
            }
        }

        Path launcher;
        List<String> lines = new ArrayList<>();
        lines.add("@echo off");
        switch (name) {
            case "redis" -> {
                Path dir = tool.resolve("Redis-x64-3.2.100");
                if (!Files.isRegularFile(dir.resolve("redis-server.exe"))) {
                    throw new BusinessException("未找到 Redis: " + dir);
                }
                launcher = logs.resolve("run-redis.cmd");
                lines.add("cd /d \"" + dir + "\"");
                lines.add(
                        "redis-server.exe redis.windows.conf >> \""
                                + logs.resolve("redis.log")
                                + "\" 2>&1");
            }
            case "minio" -> {
                Path dir = tool.resolve("minio");
                if (!Files.isRegularFile(dir.resolve("minio.exe"))) {
                    throw new BusinessException("未找到 MinIO: " + dir);
                }
                try {
                    Files.createDirectories(dir.resolve("data"));
                } catch (IOException ignored) {
                    // ignore
                }
                launcher = logs.resolve("run-minio.cmd");
                lines.add("cd /d \"" + dir + "\"");
                lines.add("set MINIO_ROOT_USER=minioadmin");
                lines.add("set MINIO_ROOT_PASSWORD=minioadmin");
                lines.add(
                        "minio.exe server data --address :9000 --console-address :9001 >> \""
                                + logs.resolve("minio.log")
                                + "\" 2>&1");
            }
            case "nacos" -> {
                Path bin = tool.resolve("nacos3/bin");
                if (!Files.isRegularFile(bin.resolve("startup.cmd"))) {
                    throw new BusinessException("未找到 Nacos: " + bin);
                }
                if (javaHome == null || javaHome.isBlank()) {
                    throw new BusinessException("未设置 JAVA_HOME，无法启动 Nacos");
                }
                launcher = logs.resolve("run-nacos.cmd");
                lines.add("set \"JAVA_HOME=" + javaHome + "\"");
                lines.add("set \"PATH=%JAVA_HOME%\\bin;%PATH%\"");
                lines.add("cd /d \"" + bin + "\"");
                lines.add(
                        "call startup.cmd -m standalone > \""
                                + logs.resolve("nacos.log")
                                + "\" 2>&1");
            }
            case "kkfileview" -> {
                Path bin = tool.resolve("kkFileView-5.0.0/bin");
                if (!Files.isRegularFile(bin.resolve("startup.bat"))) {
                    throw new BusinessException("未找到 kkFileView: " + bin);
                }
                if (javaHome == null || javaHome.isBlank()) {
                    throw new BusinessException("未设置 JAVA_HOME，无法启动 kkFileView");
                }
                launcher = logs.resolve("run-kkfileview.cmd");
                lines.add("set \"JAVA_HOME=" + javaHome + "\"");
                lines.add("set \"PATH=%JAVA_HOME%\\bin;%PATH%\"");
                lines.add("cd /d \"" + bin + "\"");
                lines.add("call startup.bat >> \"" + logs.resolve("kkfileview.log") + "\" 2>&1");
            }
            default -> throw new BusinessException("未知组件");
        }

        try {
            Files.writeString(
                    launcher, String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("写入启动脚本失败: " + e.getMessage());
        }

        try {
            String ps = System.getenv("SystemRoot");
            if (ps == null || ps.isBlank()) {
                ps = "C:\\Windows";
            }
            Path powershell =
                    Path.of(ps, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            String psExe =
                    Files.isRegularFile(powershell) ? powershell.toString() : "powershell.exe";
            new ProcessBuilder(
                            psExe,
                            "-NoProfile",
                            "-ExecutionPolicy",
                            "Bypass",
                            "-Command",
                            "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c','\""
                                    + launcher
                                    + "\"' -WindowStyle Hidden")
                    .redirectErrorStream(true)
                    .start();
            log.info("已后台启动 {} via {}", name, launcher);
        } catch (IOException e) {
            throw new BusinessException("启动失败: " + e.getMessage());
        }
    }

    private List<String> readLines(Process p) throws IOException {
        List<String> lines = new ArrayList<>();
        Charset cs = Charset.forName("GBK");
        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(p.getInputStream(), cs))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 供前端提示用的项目根路径 */
    public Map<String, Object> tipMeta() {
        Path root = resolveProjectRoot();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectRoot", root.toString());
        map.put("startCommand", "cd /d \"" + root + "\" && 启动.bat");
        map.put("restartEnabled", infraProperties.isRestartEnabled());
        return map;
    }
}
