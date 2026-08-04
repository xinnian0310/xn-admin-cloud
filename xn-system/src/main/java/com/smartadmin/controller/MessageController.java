package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.MessageReaderVO;
import com.smartadmin.dto.MessageRequest;
import com.smartadmin.dto.MessageSendRequest;
import com.smartadmin.dto.MessageVO;
import com.smartadmin.dto.MyMessageVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.MessageService;
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
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ApiResponse<PageResult<MessageVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(messageService.list(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<MessageVO> detail(@PathVariable Long id) {
        return ApiResponse.success(messageService.getById(id));
    }

    @PostMapping
    @OperLog(title = "站内信", businessType = OperBusinessType.INSERT)
    public ApiResponse<MessageVO> create(@Valid @RequestBody MessageRequest request) {
        return ApiResponse.success(messageService.create(request));
    }

    @PutMapping("/{id}")
    @OperLog(title = "站内信", businessType = OperBusinessType.UPDATE)
    public ApiResponse<MessageVO> update(@PathVariable Long id, @Valid @RequestBody MessageRequest request) {
        return ApiResponse.success(messageService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "站内信", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        messageService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "站内信", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = messageService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }

    @PostMapping("/{id}/send")
    @OperLog(title = "站内信", businessType = OperBusinessType.OTHER)
    public ApiResponse<MessageVO> send(@PathVariable Long id, @RequestBody MessageSendRequest request) {
        return ApiResponse.success(messageService.send(id, request != null ? request : new MessageSendRequest()));
    }

    @GetMapping("/{id}/readers")
    public ApiResponse<List<MessageReaderVO>> readers(@PathVariable Long id) {
        return ApiResponse.success(messageService.readers(id));
    }

    @GetMapping("/mine")
    public ApiResponse<List<MyMessageVO>> mine() {
        return ApiResponse.success(messageService.myList());
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success(Map.of("count", messageService.unreadCount()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return ApiResponse.success("已读", null);
    }

    @DeleteMapping("/mine/{id}")
    public ApiResponse<Void> deleteMine(@PathVariable Long id) {
        messageService.deleteMine(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/mine/batch-delete")
    public ApiResponse<Map<String, Integer>> batchDeleteMine(@RequestBody(required = false) IdsRequest request) {
        List<Long> ids = request == null || request.getIds() == null ? List.of() : request.getIds();
        int count = messageService.batchDeleteMine(ids);
        return ApiResponse.success("删除成功", Map.of("count", count));
    }
}
