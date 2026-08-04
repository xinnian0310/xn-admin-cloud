package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.MyNoticeVO;
import com.smartadmin.dto.NoticeReaderVO;
import com.smartadmin.dto.NoticeRequest;
import com.smartadmin.dto.NoticeVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public ApiResponse<PageResult<NoticeVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(noticeService.list(page, size, keyword, status));
    }

    @GetMapping("/mine")
    public ApiResponse<List<MyNoticeVO>> mine() {
        return ApiResponse.success(noticeService.myList());
    }

    @GetMapping("/{id}")
    public ApiResponse<NoticeVO> detail(@PathVariable Long id) {
        return ApiResponse.success(noticeService.getById(id));
    }

    @GetMapping("/{id}/readers")
    public ApiResponse<List<NoticeReaderVO>> readers(@PathVariable Long id) {
        return ApiResponse.success(noticeService.readers(id));
    }

    @PostMapping
    @OperLog(title = "公告管理", businessType = OperBusinessType.INSERT)
    public ApiResponse<NoticeVO> create(@Valid @RequestBody NoticeRequest request) {
        return ApiResponse.success("保存成功", noticeService.create(request));
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "公告管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = noticeService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }

    @PostMapping("/batch-publish")
    @OperLog(title = "公告管理", businessType = OperBusinessType.OTHER)
    public ApiResponse<Map<String, Integer>> batchPublish(@Valid @RequestBody IdsRequest request) {
        int count = noticeService.batchPublish(request.getIds());
        return ApiResponse.success("下发成功", Map.of("count", count));
    }

    @PostMapping("/batch-revoke")
    @OperLog(title = "公告管理", businessType = OperBusinessType.OTHER)
    public ApiResponse<Map<String, Integer>> batchRevoke(@Valid @RequestBody IdsRequest request) {
        int count = noticeService.batchRevoke(request.getIds());
        return ApiResponse.success("撤回成功", Map.of("count", count));
    }

    @PutMapping("/{id}")
    @OperLog(title = "公告管理", businessType = OperBusinessType.UPDATE)
    public ApiResponse<NoticeVO> update(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        return ApiResponse.success("更新成功", noticeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "公告管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/{id}/publish")
    @OperLog(title = "公告管理", businessType = OperBusinessType.OTHER)
    public ApiResponse<NoticeVO> publish(@PathVariable Long id) {
        return ApiResponse.success("下发成功", noticeService.publish(id));
    }

    @PostMapping("/{id}/revoke")
    @OperLog(title = "公告管理", businessType = OperBusinessType.OTHER)
    public ApiResponse<NoticeVO> revoke(@PathVariable Long id) {
        return ApiResponse.success("撤回成功", noticeService.revoke(id));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        noticeService.markRead(id);
        return ApiResponse.success("已标记已读", null);
    }
}
