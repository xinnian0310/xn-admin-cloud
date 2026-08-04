package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.DictDataRequest;
import com.smartadmin.dto.DictDataVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.SysDictData;
import com.smartadmin.repository.SysDictDataRepository;
import com.smartadmin.repository.SysDictTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictDataService {

    private final SysDictDataRepository dictDataRepository;
    private final SysDictTypeRepository dictTypeRepository;
    private final RbacService rbacService;
    private final AppCacheService appCacheService;

    public PageResult<DictDataVO> list(String dictType, int page, int size, String keyword, Integer status) {
        rbacService.checkPermission("dict-data:view");
        if (!StringUtils.hasText(dictType)) {
            throw new BusinessException("字典类型不能为空");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sort"));
        Page<SysDictData> result = dictDataRepository.search(
                dictType, StringUtils.hasText(keyword) ? keyword.trim() : "", status, pageable);
        List<DictDataVO> records = result.getContent().stream().map(DictDataVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    /** 供业务页面动态取某字典类型下的启用项（下拉/标签渲染） */
    public List<DictDataVO> listByType(String dictType) {
        return appCacheService.getDict(dictType, new tools.jackson.core.type.TypeReference<>() {
        }, () -> dictDataRepository.findByDictTypeAndStatusOrderBySortAscIdAsc(dictType, 1).stream()
                .map(DictDataVO::from)
                .toList());
    }

    public DictDataVO getById(Long id) {
        rbacService.checkPermission("dict-data:view");
        return DictDataVO.from(findData(id));
    }

    @Transactional
    public DictDataVO create(DictDataRequest request) {
        rbacService.checkPermission("dict-data:create");
        validateDictType(request.getDictType());
        if (dictDataRepository.existsByDictTypeAndValue(request.getDictType(), request.getValue())) {
            throw new BusinessException("该字典下键值已存在");
        }
        SysDictData data = new SysDictData();
        applyRequest(data, request);
        SysDictData saved = dictDataRepository.save(data);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            clearOtherDefaults(saved);
        }
        appCacheService.evictDict(saved.getDictType());
        return DictDataVO.from(saved);
    }

    @Transactional
    public DictDataVO update(Long id, DictDataRequest request) {
        rbacService.checkPermission("dict-data:update");
        SysDictData data = findData(id);
        String oldType = data.getDictType();
        validateDictType(request.getDictType());
        if ((!data.getDictType().equals(request.getDictType()) || !data.getValue().equals(request.getValue()))
                && dictDataRepository.existsByDictTypeAndValueAndIdNot(request.getDictType(), request.getValue(), id)) {
            throw new BusinessException("该字典下键值已存在");
        }
        applyRequest(data, request);
        SysDictData saved = dictDataRepository.save(data);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            clearOtherDefaults(saved);
        }
        appCacheService.evictDict(oldType);
        appCacheService.evictDict(saved.getDictType());
        return DictDataVO.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("dict-data:delete");
        SysDictData data = findData(id);
        String type = data.getDictType();
        dictDataRepository.delete(data);
        appCacheService.evictDict(type);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("dict-data:delete");
        int count = 0;
        for (Long id : ids) {
            SysDictData data = findData(id);
            String type = data.getDictType();
            dictDataRepository.delete(data);
            appCacheService.evictDict(type);
            count++;
        }
        return count;
    }

    private void clearOtherDefaults(SysDictData current) {
        List<SysDictData> others = dictDataRepository.findByDictTypeAndIdNot(current.getDictType(), current.getId());
        for (SysDictData other : others) {
            if (Boolean.TRUE.equals(other.getIsDefault())) {
                other.setIsDefault(false);
                dictDataRepository.save(other);
            }
        }
    }

    private void validateDictType(String dictType) {
        dictTypeRepository.findByType(dictType)
                .orElseThrow(() -> new BusinessException("字典类型不存在"));
    }

    private void applyRequest(SysDictData data, DictDataRequest request) {
        data.setDictType(request.getDictType().trim());
        data.setLabel(request.getLabel().trim());
        data.setValue(request.getValue().trim());
        data.setSort(request.getSort() != null ? request.getSort() : 0);
        data.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        data.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        data.setListClass(request.getListClass());
        data.setRemark(request.getRemark());
    }

    private SysDictData findData(Long id) {
        return dictDataRepository.findById(id)
                .orElseThrow(() -> new BusinessException("字典数据不存在"));
    }
}
