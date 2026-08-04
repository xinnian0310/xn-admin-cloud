package com.smartadmin.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class IdsRequest {

    @NotEmpty(message = "请选择至少一项")
    private List<Long> ids;
}
