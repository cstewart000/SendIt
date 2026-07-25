package com.timbernest.design;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RepairActionLogRepository extends JpaRepository<RepairActionLog, Long> {
    List<RepairActionLog> findByDesignVersionIdOrderByCreatedAtAsc(Long designVersionId);
}
