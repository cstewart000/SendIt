package com.timbernest.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessRepository extends JpaRepository<ProcessDef, Long> {
    List<ProcessDef> findByMachineId(Long machineId);
}
