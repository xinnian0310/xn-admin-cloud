package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.DictTypeRequest;
import com.smartadmin.dto.DictTypeVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.SysDictType;
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
public class DictTypeService {

    private final SysDictTypeRepository dictTypeRepository;
    private final SysDictDataRepository dictDataRepository;
    private final RbacService rbacService;

    public PageResult<DictTypeVO> list(int page, int size, String keyword, Integer status) {
        rbacService.checkPermission("dict-type:view");
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<SysDictType> result = dictTypeRepository.search(
                StringUtils.hasText(keyword) ? keyword.trim() : "", status, pageable);
        List<DictTypeVO> records = result.getContent().stream().map(DictTypeVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public List<DictTypeVO> listOptions() {
        rbacService.checkPermission("dict-type:view");
        return dictTypeRepository.findByStatusOrderByIdAsc(1).stream().map(DictTypeVO::from).toList();
    }

    public DictTypeVO getById(Long id) {
        rbacService.checkPermission("dict-type:view");
        return DictTypeVO.from(findType(id));
    }

    @Transactional
    public DictTypeVO create(DictTypeRequest request) {
        rbacService.checkPermission("dict-type:create");
        if (dictTypeRepository.existsByType(request.getType())) {
            throw new BusinessException("字典类型编码已存在");
        }
        SysDictType type = new SysDictType();
        applyRequest(type, request);
        type.setBuiltIn(false);
        return DictTypeVO.from(dictTypeRepository.save(type));
    }

    @Transactional
    public DictTypeVO update(Long id, DictTypeRequest request) {
        rbacService.checkPermission("dict-type:update");
        SysDictType type = findType(id);
        if (Boolean.TRUE.equals(type.getBuiltIn())) {
            type.setName(request.getName());
            type.setRemark(request.getRemark());
            if (request.getStatus() != null) {
                type.setStatus(request.getStatus());
            }
        } else {
            if (!type.getType().equals(request.getType()) && dictTypeRepository.existsByType(request.getType())) {
                throw new BusinessException("字典类型编码已存在");
            }
            applyRequest(type, request);
        }
        return DictTypeVO.from(dictTypeRepository.save(type));
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("dict-type:delete");
        deleteInternal(id);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("dict-type:delete");
        int count = 0;
        for (Long id : ids) {
            deleteInternal(id);
            count++;
        }
        return count;
    }

    private void deleteInternal(Long id) {
        SysDictType type = findType(id);
        if (Boolean.TRUE.equals(type.getBuiltIn())) {
            throw new BusinessException("内置字典不可删除：" + type.getName());
        }
        if (dictDataRepository.countByDictType(type.getType()) > 0) {
            throw new BusinessException("该字典下仍有字典数据，请先清理：" + type.getName());
        }
        dictTypeRepository.delete(type);
    }

    private void applyRequest(SysDictType type, DictTypeRequest request) {
        type.setName(request.getName().trim());
        type.setType(request.getType().trim());
        type.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        type.setRemark(request.getRemark());
    }

    private SysDictType findType(Long id) {
        return dictTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("字典类型不存在"));
    }
}
