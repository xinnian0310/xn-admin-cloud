package com.smartadmin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoleIdsRequest {

    private List<Long> roleIds = new ArrayList<>();
}
