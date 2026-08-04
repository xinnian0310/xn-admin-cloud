package com.smartadmin.config.nacos;

import java.util.Collections;
import java.util.Set;
import org.springframework.context.ApplicationEvent;

/** Nacos 配置热更新完成事件。 */
public class NacosConfigRefreshedEvent extends ApplicationEvent {

    private final String dataId;
    private final String group;
    private final Set<String> keys;

    public NacosConfigRefreshedEvent(Object source, String dataId, String group, Set<String> keys) {
        super(source);
        this.dataId = dataId;
        this.group = group;
        this.keys = keys == null ? Set.of() : Collections.unmodifiableSet(keys);
    }

    public String getDataId() {
        return dataId;
    }

    public String getGroup() {
        return group;
    }

    public Set<String> getKeys() {
        return keys;
    }
}
