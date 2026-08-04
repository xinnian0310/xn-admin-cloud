package com.smartadmin.repository;

import com.smartadmin.entity.SysFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysFileRepository extends JpaRepository<SysFile, Long> {

    Optional<SysFile> findByObjectKey(String objectKey);

    void deleteByObjectKey(String objectKey);

    @Query("""
            SELECT f FROM SysFile f
            WHERE f.deletedAt IS NULL
              AND f.prefix = :prefix
              AND (:keyword = ''
                OR f.originalName LIKE CONCAT('%', :keyword, '%')
                OR f.objectKey LIKE CONCAT('%', :keyword, '%')
                OR f.contentType LIKE CONCAT('%', :keyword, '%'))
            ORDER BY f.id DESC
            """)
    List<SysFile> findByPrefix(@Param("prefix") String prefix, @Param("keyword") String keyword);

    @Query("""
            SELECT f FROM SysFile f
            WHERE f.deletedAt IS NULL
              AND (:keyword = ''
                OR f.originalName LIKE CONCAT('%', :keyword, '%')
                OR f.objectKey LIKE CONCAT('%', :keyword, '%')
                OR f.contentType LIKE CONCAT('%', :keyword, '%'))
            ORDER BY f.id DESC
            """)
    List<SysFile> searchAll(@Param("keyword") String keyword);

    @Query("SELECT f.objectKey FROM SysFile f WHERE f.deletedAt IS NOT NULL")
    List<String> findDeletedObjectKeys();
}
