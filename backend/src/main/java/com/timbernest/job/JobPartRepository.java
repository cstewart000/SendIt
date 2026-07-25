package com.timbernest.job;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobPartRepository extends JpaRepository<JobPart, Long> {
    List<JobPart> findByJobId(Long jobId);
}
