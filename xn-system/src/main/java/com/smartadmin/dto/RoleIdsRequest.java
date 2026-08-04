package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RoleIdsRequest {

    private List<Long> roleIds = new ArrayList<>();
}
