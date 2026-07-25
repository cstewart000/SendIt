package com.timbernest.job;

import com.timbernest.common.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    List<Job> findByStatusInOrderByUpdatedAtDesc(List<JobStatus> statuses);
}
