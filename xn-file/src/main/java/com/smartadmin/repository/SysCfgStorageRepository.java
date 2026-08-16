package com.smartadmin.repository;

import com.smartadmin.entity.SysCfgStorage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysCfgStorageRepository extends JpaRepository<SysCfgStorage, Long> {
    Optional<SysCfgStorage> findByName(String name);

    void deleteAllInBatch();
}
