package com.smartadmin.repository;

import com.smartadmin.entity.SysDictData;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysDictDataRepository extends JpaRepository<SysDictData, Long> {

    @Query(
            "SELECT d FROM SysDictData d WHERE d.dictType = :dictType"
                    + " AND (:keyword = '' OR d.label LIKE CONCAT('%', :keyword, '%') OR d.value LIKE CONCAT('%', :keyword, '%'))"
                    + " AND (:status IS NULL OR d.status = :status)"
                    + " ORDER BY d.sort ASC, d.id ASC")
    Page<SysDictData> search(
            @Param("dictType") String dictType,
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            Pageable pageable);

    List<SysDictData> findByDictTypeAndStatusOrderBySortAscIdAsc(String dictType, Integer status);

    long countByDictType(String dictType);

    boolean existsByDictTypeAndValueAndIdNot(String dictType, String value, Long id);

    boolean existsByDictTypeAndValue(String dictType, String value);

    List<SysDictData> findByDictTypeAndIdNot(String dictType, Long id);

    List<SysDictData> findByDictType(String dictType);
}
