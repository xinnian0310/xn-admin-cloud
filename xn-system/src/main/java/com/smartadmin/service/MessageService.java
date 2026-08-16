package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.AttachmentSupport;
import com.smartadmin.dto.MessageReaderVO;
import com.smartadmin.dto.MessageRequest;
import com.smartadmin.dto.MessageSendRequest;
import com.smartadmin.dto.MessageVO;
import com.smartadmin.dto.MyMessageVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.MessageStatus;
import com.smartadmin.entity.SysMessage;
import com.smartadmin.entity.SysMessageReceiver;
import com.smartadmin.entity.User;
import com.smartadmin.repository.SysMessageReceiverRepository;
import com.smartadmin.repository.SysMessageRepository;
import com.smartadmin.repository.UserRepository;
import com.smartadmin.websocket.NoticeSessionHub;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final SysMessageRepository messageRepository;
    private final SysMessageReceiverRepository receiverRepository;
    private final UserRepository userRepository;
    private final RbacService rbacService;
    private final NoticeSessionHub sessionHub;
    private final DataScopeService dataScopeService;
    private final FileManageService fileManageService;

    public PageResult<MessageVO> list(int page, int size, String keyword, String status) {
        rbacService.checkPermission("message:view");
        MessageStatus statusEnum = parseStatus(status);
        DataScopeService.OwnerFilter ownerFilter = dataScopeService.resolveOwnerFilter();
        Page<SysMessage> result =
                messageRepository.search(
                        StringUtils.hasText(keyword) ? keyword.trim() : "",
                        statusEnum,
                        ownerFilter.ownerIds(),
                        ownerFilter.unrestricted(),
                        PageRequest.of(page, size));
        Map<Long, String> senderNames = loadSenderNames(result.getContent());
        List<MessageVO> records =
                result.getContent().stream()
                        .map(m -> toAdminVO(m, senderNames.get(m.getSenderId())))
                        .toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public MessageVO getById(Long id) {
        rbacService.checkPermission("message:view");
        SysMessage message = findMessage(id);
        dataScopeService.assertOwnerAccessible(message.getSenderId());
        String senderName = resolveSenderName(message.getSenderId());
        return toAdminVO(message, senderName);
    }

    @Transactional
    public MessageVO create(MessageRequest request) {
        rbacService.checkPermission("message:create");
        SysMessage message = new SysMessage();
        message.setTitle(request.getTitle().trim());
        message.setContent(request.getContent());
        applyAttachments(message, request);
        message.setStatus(MessageStatus.DRAFT);
        message.setSenderId(rbacService.currentUser().getId());
        return toAdminVO(messageRepository.save(message), currentSenderName());
    }

    @Transactional
    public MessageVO update(Long id, MessageRequest request) {
        rbacService.checkPermission("message:update");
        SysMessage message = findMessage(id);
        dataScopeService.assertOwnerAccessible(message.getSenderId());
        if (message.getStatus() != MessageStatus.DRAFT) {
            throw new BusinessException("仅草稿可编辑");
        }
        message.setTitle(request.getTitle().trim());
        message.setContent(request.getContent());
        applyAttachments(message, request);
        return toAdminVO(messageRepository.save(message), currentSenderName());
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("message:delete");
        deleteInternal(id);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("message:delete");
        int count = 0;
        for (Long id : ids) {
            deleteInternal(id);
            count++;
        }
        return count;
    }

    private void deleteInternal(Long id) {
        SysMessage message = findMessage(id);
        dataScopeService.assertOwnerAccessible(message.getSenderId());
        if (message.getStatus() != MessageStatus.DRAFT) {
            throw new BusinessException("仅草稿可删除：" + message.getTitle());
        }
        receiverRepository.deleteByMessageId(id);
        messageRepository.delete(message);
    }

    @Transactional
    public MessageVO send(Long id, MessageSendRequest request) {
        rbacService.checkPermission("message:send");
        SysMessage message = findMessage(id);
        dataScopeService.assertOwnerAccessible(message.getSenderId());
        if (message.getStatus() != MessageStatus.DRAFT) {
            throw new BusinessException("仅草稿可发送");
        }
        receiverRepository.deleteByMessageId(id);

        List<User> targets = resolveTargets(request);
        if (targets.isEmpty()) {
            throw new BusinessException("请指定接收用户");
        }

        LocalDateTime now = LocalDateTime.now();
        message.setStatus(MessageStatus.SENT);
        message.setSenderId(rbacService.currentUser().getId());
        message.setSentAt(now);
        messageRepository.save(message);

        List<SysMessageReceiver> receivers =
                targets.stream()
                        .map(
                                user -> {
                                    SysMessageReceiver receiver = new SysMessageReceiver();
                                    receiver.setMessageId(message.getId());
                                    receiver.setUserId(user.getId());
                                    return receiver;
                                })
                        .toList();
        receiverRepository.saveAll(receivers);

        for (User user : targets) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "message:sent");
            payload.put("id", message.getId());
            payload.put("title", message.getTitle());
            payload.put("sentAt", now.toString());
            sessionHub.sendToUser(user.getId(), payload);
        }

        return toAdminVO(message, currentSenderName());
    }

    public List<MessageReaderVO> readers(Long id) {
        rbacService.checkPermission("message:view");
        SysMessage message = findMessage(id);
        dataScopeService.assertOwnerAccessible(message.getSenderId());
        List<SysMessageReceiver> readers = receiverRepository.findReaders(id);
        Map<Long, User> users =
                userRepository
                        .findAllById(readers.stream().map(SysMessageReceiver::getUserId).toList())
                        .stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        return readers.stream()
                .map(
                        r -> {
                            MessageReaderVO vo = new MessageReaderVO();
                            vo.setUserId(r.getUserId());
                            vo.setReadAt(r.getReadAt());
                            User user = users.get(r.getUserId());
                            if (user != null) {
                                vo.setUsername(user.getUsername());
                                vo.setNickname(user.getNickname());
                            }
                            return vo;
                        })
                .toList();
    }

    public List<MyMessageVO> myList() {
        User user = rbacService.currentUser();
        List<SysMessageReceiver> receivers =
                receiverRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (receivers.isEmpty()) {
            return List.of();
        }
        Map<Long, SysMessage> messages =
                messageRepository
                        .findAllById(
                                receivers.stream().map(SysMessageReceiver::getMessageId).toList())
                        .stream()
                        .collect(Collectors.toMap(SysMessage::getId, Function.identity()));

        Map<Long, String> senderNames = loadSenderNames(messages.values().stream().toList());

        return receivers.stream()
                .map(
                        r -> {
                            SysMessage message = messages.get(r.getMessageId());
                            if (message == null || message.getStatus() != MessageStatus.SENT) {
                                return null;
                            }
                            return MyMessageVO.from(
                                    message,
                                    r.getReadAt() != null,
                                    r.getReadAt(),
                                    r.getCreatedAt(),
                                    senderNames.get(message.getSenderId()));
                        })
                .filter(Objects::nonNull)
                .toList();
    }

    public long unreadCount() {
        User user = rbacService.currentUser();
        return receiverRepository.countByUserIdAndReadAtIsNull(user.getId());
    }

    @Transactional
    public void markRead(Long id) {
        User user = rbacService.currentUser();
        SysMessage message = findMessage(id);
        if (message.getStatus() != MessageStatus.SENT) {
            throw new BusinessException("消息不可读");
        }
        SysMessageReceiver receiver =
                receiverRepository
                        .findByMessageIdAndUserId(id, user.getId())
                        .orElseThrow(() -> new BusinessException("未收到该消息"));
        if (receiver.getReadAt() == null) {
            receiver.setReadAt(LocalDateTime.now());
            receiverRepository.save(receiver);
        }
    }

    /** 从「我的消息」收件箱移除（仅删接收关系，不删原站内信） */
    @Transactional
    public void deleteMine(Long id) {
        User user = rbacService.currentUser();
        SysMessageReceiver receiver =
                receiverRepository
                        .findByMessageIdAndUserId(id, user.getId())
                        .orElseThrow(() -> new BusinessException("未收到该消息"));
        receiverRepository.delete(receiver);
    }

    @Transactional
    public int batchDeleteMine(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        User user = rbacService.currentUser();
        int count = 0;
        for (Long id : ids) {
            var opt = receiverRepository.findByMessageIdAndUserId(id, user.getId());
            if (opt.isPresent()) {
                receiverRepository.delete(opt.get());
                count++;
            }
        }
        return count;
    }

    private List<User> resolveTargets(MessageSendRequest request) {
        if (Boolean.TRUE.equals(request.getSendToAll())) {
            return dataScopeService.listAccessibleActiveUsers();
        }
        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            return List.of();
        }
        List<User> selected =
                userRepository.findAllById(request.getUserIds()).stream()
                        .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                        .toList();
        return dataScopeService.filterAccessibleUsers(selected);
    }

    private MessageStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return MessageStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("无效的消息状态");
        }
    }

    private SysMessage findMessage(Long id) {
        return messageRepository.findById(id).orElseThrow(() -> new BusinessException("消息不存在"));
    }

    private void applyAttachments(SysMessage message, MessageRequest request) {
        var items = new ArrayList<>(AttachmentSupport.normalize(request.getAttachments()));
        fileManageService.enrichAttachments(items);
        message.setAttachments(items.isEmpty() ? null : List.copyOf(items));
    }

    private MessageVO toAdminVO(SysMessage message, String senderName) {
        MessageVO vo = MessageVO.from(message);
        vo.setSenderName(senderName);
        if (message.getStatus() == MessageStatus.SENT) {
            vo.setTotalCount(receiverRepository.countByMessageId(message.getId()));
            vo.setReadCount(receiverRepository.countByMessageIdAndReadAtIsNotNull(message.getId()));
        }
        return vo;
    }

    private Map<Long, String> loadSenderNames(List<SysMessage> messages) {
        List<Long> ids =
                messages.stream()
                        .map(SysMessage::getSenderId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(
                        Collectors.toMap(
                                User::getId,
                                u ->
                                        StringUtils.hasText(u.getNickname())
                                                ? u.getNickname()
                                                : u.getUsername()));
    }

    private String resolveSenderName(Long senderId) {
        if (senderId == null) {
            return null;
        }
        return userRepository
                .findById(senderId)
                .map(u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername())
                .orElse(null);
    }

    private String currentSenderName() {
        User user = rbacService.currentUser();
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
