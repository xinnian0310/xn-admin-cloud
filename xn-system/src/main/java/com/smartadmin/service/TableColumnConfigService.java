package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.TableColumnConfigRequest;
import com.smartadmin.dto.TableColumnConfigVO;
import com.smartadmin.dto.TableColumnSettingDTO;
import com.smartadmin.entity.SysTableColumnConfig;
import com.smartadmin.entity.User;
import com.smartadmin.repository.SysTableColumnConfigRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class TableColumnConfigService {

    private final SysTableColumnConfigRepository repository;
    private final RbacService rbacService;
    private final ObjectMapper objectMapper;

    public TableColumnConfigVO getForCurrentUser(String tableKey) {
        if (!StringUtils.hasText(tableKey)) {
            throw new BusinessException("tableKey 不能为空");
        }
        User user = rbacService.currentUser();
        TableColumnConfigVO vo = new TableColumnConfigVO();
        vo.setTableKey(tableKey);
        repository
                .findByUserIdAndTableKey(user.getId(), tableKey)
                .ifPresent(
                        config -> {
                            vo.setColumns(parseColumns(config.getColumnsJson()));
                        });
        return vo;
    }

    @Transactional
    public TableColumnConfigVO saveForCurrentUser(TableColumnConfigRequest request) {
        User user = rbacService.currentUser();
        String tableKey = request.getTableKey().trim();
        List<TableColumnSettingDTO> columns = normalizeColumns(request.getColumns());

        SysTableColumnConfig config =
                repository
                        .findByUserIdAndTableKey(user.getId(), tableKey)
                        .orElseGet(SysTableColumnConfig::new);
        config.setUserId(user.getId());
        config.setTableKey(tableKey);
        config.setColumnsJson(writeColumns(columns));
        repository.save(config);

        TableColumnConfigVO vo = new TableColumnConfigVO();
        vo.setTableKey(tableKey);
        vo.setColumns(columns);
        return vo;
    }

    private List<TableColumnSettingDTO> normalizeColumns(List<TableColumnSettingDTO> columns) {
        List<TableColumnSettingDTO> result = new ArrayList<>();
        int index = 0;
        for (TableColumnSettingDTO item : columns) {
            if (item == null || !StringUtils.hasText(item.getKey())) {
                continue;
            }
            TableColumnSettingDTO dto = new TableColumnSettingDTO();
            dto.setKey(item.getKey().trim());
            dto.setProp(item.getProp());
            dto.setLabel(item.getLabel());
            dto.setWidth(item.getWidth());
            dto.setVisible(item.getVisible() == null || item.getVisible());
            dto.setSort(item.getSort() != null ? item.getSort() : index);
            result.add(dto);
            index++;
        }
        result.sort(
                Comparator.comparing(
                        TableColumnSettingDTO::getSort, Comparator.nullsLast(Integer::compareTo)));
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setSort(i);
        }
        return result;
    }

    private List<TableColumnSettingDTO> parseColumns(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json, new TypeReference<List<TableColumnSettingDTO>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeColumns(List<TableColumnSettingDTO> columns) {
        try {
            return objectMapper.writeValueAsString(columns);
        } catch (Exception e) {
            throw new BusinessException("列配置序列化失败");
        }
    }
}
