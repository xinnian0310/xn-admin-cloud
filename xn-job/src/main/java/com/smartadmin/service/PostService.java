package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.ImportResultVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.dto.PostImportRow;
import com.smartadmin.dto.PostRequest;
import com.smartadmin.dto.PostVO;
import com.smartadmin.entity.SysPost;
import com.smartadmin.repository.SysPostRepository;
import com.smartadmin.repository.UserRepository;
import com.smartadmin.util.ExcelExportUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final int EXPORT_LIMIT = 10000;

    private final SysPostRepository postRepository;
    private final UserRepository userRepository;
    private final RbacService rbacService;

    public PageResult<PostVO> list(int page, int size, String keyword, Integer status) {
        rbacService.checkPermission("post:view");
        PageRequest pageable =
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sort", "id"));
        Page<SysPost> result =
                postRepository.search(
                        StringUtils.hasText(keyword) ? keyword.trim() : "", status, pageable);
        List<PostVO> records = result.getContent().stream().map(PostVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public List<PostVO> listOptions() {
        return postRepository.findByStatusOrderBySortAscIdAsc(1).stream()
                .map(PostVO::from)
                .toList();
    }

    public PostVO getById(Long id) {
        rbacService.checkPermission("post:view");
        return PostVO.from(findPost(id));
    }

    @Transactional
    public PostVO create(PostRequest request) {
        rbacService.checkPermission("post:create");
        if (postRepository.existsByCode(request.getCode().trim())) {
            throw new BusinessException("岗位编码已存在");
        }
        SysPost post = new SysPost();
        applyRequest(post, request);
        post.setBuiltIn(false);
        return PostVO.from(postRepository.save(post));
    }

    @Transactional
    public PostVO update(Long id, PostRequest request) {
        rbacService.checkPermission("post:update");
        SysPost post = findPost(id);
        String newCode = request.getCode().trim();
        if (!post.getCode().equalsIgnoreCase(newCode) && postRepository.existsByCode(newCode)) {
            throw new BusinessException("岗位编码已存在");
        }
        if (Boolean.TRUE.equals(post.getBuiltIn())) {
            post.setName(request.getName().trim());
            post.setSort(request.getSort() != null ? request.getSort() : post.getSort());
            post.setRemark(request.getRemark());
            if (request.getStatus() != null) {
                post.setStatus(request.getStatus());
            }
        } else {
            applyRequest(post, request);
        }
        return PostVO.from(postRepository.save(post));
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("post:delete");
        deleteInternal(id);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("post:delete");
        int count = 0;
        for (Long id : ids) {
            deleteInternal(id);
            count++;
        }
        return count;
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        rbacService.checkPermission("post:update");
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态无效");
        }
        SysPost post = findPost(id);
        post.setStatus(status);
        postRepository.save(post);
    }

    @Transactional
    public ImportResultVO importPosts(List<PostImportRow> rows) {
        rbacService.checkPermission("post:import");
        ImportResultVO result = new ImportResultVO();
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("导入数据为空");
        }
        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            PostImportRow row = rows.get(i);
            try {
                importOne(row);
                result.addSuccess();
            } catch (BusinessException ex) {
                result.addError(rowNum, ex.getMessage());
            } catch (Exception ex) {
                result.addError(rowNum, "导入失败: " + ex.getMessage());
            }
        }
        return result;
    }

    public byte[] exportExcel(String keyword, Integer status) {
        rbacService.checkPermission("post:export");
        List<SysPost> rows =
                postRepository.searchAll(
                        StringUtils.hasText(keyword) ? keyword.trim() : "", status);
        if (rows.size() > EXPORT_LIMIT) {
            rows = rows.subList(0, EXPORT_LIMIT);
        }
        return ExcelExportUtil.toXlsx(
                "岗位",
                List.of("岗位编码", "岗位名称", "排序", "状态", "备注"),
                rows.stream()
                        .map(
                                p ->
                                        List.of(
                                                nullToEmpty(p.getCode()),
                                                nullToEmpty(p.getName()),
                                                p.getSort() == null
                                                        ? "0"
                                                        : String.valueOf(p.getSort()),
                                                p.getStatus() != null && p.getStatus() == 1
                                                        ? "启用"
                                                        : "停用",
                                                nullToEmpty(p.getRemark())))
                        .toList());
    }

    private void importOne(PostImportRow row) {
        if (row == null
                || !StringUtils.hasText(row.getCode())
                || !StringUtils.hasText(row.getName())) {
            throw new BusinessException("岗位编码和名称不能为空");
        }
        String code = row.getCode().trim();
        if (postRepository.existsByCode(code)) {
            throw new BusinessException("岗位编码已存在: " + code);
        }
        SysPost post = new SysPost();
        post.setCode(code);
        post.setName(row.getName().trim());
        post.setSort(row.getSort() != null ? row.getSort() : 0);
        post.setStatus(row.getStatus() != null ? row.getStatus() : 1);
        post.setRemark(row.getRemark());
        post.setBuiltIn(false);
        postRepository.save(post);
    }

    private void deleteInternal(Long id) {
        SysPost post = findPost(id);
        if (Boolean.TRUE.equals(post.getBuiltIn())) {
            throw new BusinessException("内置岗位不可删除：" + post.getName());
        }
        long userCount = userRepository.countByPostId(id);
        if (userCount > 0) {
            throw new BusinessException("该岗位下仍有 " + userCount + " 个用户，无法删除");
        }
        postRepository.delete(post);
    }

    private void applyRequest(SysPost post, PostRequest request) {
        post.setCode(request.getCode().trim());
        post.setName(request.getName().trim());
        post.setSort(request.getSort() != null ? request.getSort() : 0);
        post.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        post.setRemark(request.getRemark());
    }

    private SysPost findPost(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new BusinessException("岗位不存在"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
