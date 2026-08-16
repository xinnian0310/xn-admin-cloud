package com.smartadmin.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartadmin.dto.AttachmentItem;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import org.springframework.util.StringUtils;

/** 把附件列表存成 JSON 文本，避免依赖数据库的 json 类型。 */
@Converter
public class AttachmentListConverter implements AttributeConverter<List<AttachmentItem>, String> {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static final TypeReference<List<AttachmentItem>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<AttachmentItem> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception ex) {
            throw new IllegalStateException("附件列表序列化失败", ex);
        }
    }

    @Override
    public List<AttachmentItem> convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("附件列表反序列化失败", ex);
        }
    }
}
