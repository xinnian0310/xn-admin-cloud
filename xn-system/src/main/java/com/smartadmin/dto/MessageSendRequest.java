package com.smartadmin.dto;

import java.util.List;
import lombok.Data;

@Data
public class MessageSendRequest {

    /** 指定接收用户；为空且 sendToAll=true 时发给全部启用用户 */
    private List<Long> userIds;

    private Boolean sendToAll;
}
