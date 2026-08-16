package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.AttachmentUploadVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.AttachmentService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping("/upload")
    @OperLog(title = "业务附件", businessType = OperBusinessType.IMPORT)
    public ApiResponse<AttachmentUploadVO> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        return ApiResponse.success("上传成功", attachmentService.upload(file));
    }
}
