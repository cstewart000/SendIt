package com.timbernest.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ToolRepository extends JpaRepository<Tool, Long> {
    List<Tool> findByMachineId(Long machineId);
}
