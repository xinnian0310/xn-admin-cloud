package com.smartadmin.config;

import com.smartadmin.entity.SysDictData;
import com.smartadmin.entity.SysDictType;
import com.smartadmin.repository.SysDictDataRepository;
import com.smartadmin.repository.SysDictTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 幂等播种演示字典数据，方便开箱即用体验字典管理功能 */
@Component
@Order(6)
@RequiredArgsConstructor
public class DictInitializer implements CommandLineRunner {

    private final SysDictTypeRepository dictTypeRepository;
    private final SysDictDataRepository dictDataRepository;

    @Override
    @Transactional
    public void run(String... args) {
        ensureType("通用状态", "sys_common_status", "系统通用启用/禁用状态", data -> {
            ensureData("sys_common_status", "启用", "1", 1, "success", true);
            ensureData("sys_common_status", "禁用", "0", 2, "danger", false);
        });
        ensureType("用户性别", "sys_user_sex", "用户性别字典", data -> {
            ensureData("sys_user_sex", "男", "0", 1, "primary", true);
            ensureData("sys_user_sex", "女", "1", 2, "danger", false);
            ensureData("sys_user_sex", "未知", "2", 3, "info", false);
        });
    }

    private void ensureType(String name, String type, String remark, java.util.function.Consumer<Void> seedData) {
        SysDictType existing = dictTypeRepository.findByType(type).orElse(null);
        if (existing == null) {
            SysDictType created = new SysDictType();
            created.setName(name);
            created.setType(type);
            created.setStatus(1);
            created.setRemark(remark);
            created.setBuiltIn(true);
            dictTypeRepository.save(created);
        }
        seedData.accept(null);
    }

    private void ensureData(String dictType, String label, String value, int sort, String listClass, boolean isDefault) {
        if (dictDataRepository.existsByDictTypeAndValue(dictType, value)) {
            return;
        }
        SysDictData data = new SysDictData();
        data.setDictType(dictType);
        data.setLabel(label);
        data.setValue(value);
        data.setSort(sort);
        data.setStatus(1);
        data.setIsDefault(isDefault);
        data.setListClass(listClass);
        dictDataRepository.save(data);
    }
}
