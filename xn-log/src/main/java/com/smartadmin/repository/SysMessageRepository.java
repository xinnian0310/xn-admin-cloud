package com.smartadmin.repository;

import com.smartadmin.entity.MessageStatus;
import com.smartadmin.entity.SysMessage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysMessageRepository extends JpaRepository<SysMessage, Long> {

    @Query(
            value =
                    """
                    SELECT m FROM SysMessage m
                    WHERE (:keyword = ''
                       OR m.title LIKE CONCAT('%', :keyword, '%'))
                      AND (:status IS NULL OR m.status = :status)
                      AND (:ownerUnrestricted = true OR m.senderId IN :ownerIds)
                    ORDER BY m.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(m) FROM SysMessage m
                    WHERE (:keyword = ''
                       OR m.title LIKE CONCAT('%', :keyword, '%'))
                      AND (:status IS NULL OR m.status = :status)
                      AND (:ownerUnrestricted = true OR m.senderId IN :ownerIds)
                    """)
    Page<SysMessage> search(
            @Param("keyword") String keyword,
            @Param("status") MessageStatus status,
            @Param("ownerIds") List<Long> ownerIds,
            @Param("ownerUnrestricted") boolean ownerUnrestricted,
            Pageable pageable);
}
