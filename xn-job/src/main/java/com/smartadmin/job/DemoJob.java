package com.smartadmin.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("demoJob")
public class DemoJob {

    public void heartbeat() {
        log.info("[DemoJob] heartbeat at {}", java.time.LocalDateTime.now());
    }
}
