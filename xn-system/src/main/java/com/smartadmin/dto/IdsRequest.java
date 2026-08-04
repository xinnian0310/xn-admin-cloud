package com.smartadmin.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class IdsRequest {

    @NotEmpty(message = "请选择至少一项")
    private List<Long> ids;
}
