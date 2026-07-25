package com.timbernest.admin;

import com.timbernest.common.JobStatus;
import com.timbernest.job.Job;
import com.timbernest.job.JobDtos;
import com.timbernest.job.JobRepository;
import com.timbernest.job.JobService;
import com.timbernest.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/jobs")
public class AdminJobController {
    private static final Logger log = LoggerFactory.getLogger(AdminJobController.class);
    private final JobRepository jobs;
    private final JobService jobService;
    private final AuditLogRepository audit;

    public AdminJobController(JobRepository jobs, JobService jobService, AuditLogRepository audit) {
        this.jobs = jobs;
        this.jobService = jobService;
        this.audit = audit;
    }

    @GetMapping
    public List<JobDtos.JobView> queue() {
        return jobs.findByStatusInOrderByUpdatedAtDesc(List.of(
                JobStatus.READY_FOR_PRODUCTION, JobStatus.ORDERED, JobStatus.IN_PRODUCTION,
                JobStatus.QUOTED, JobStatus.PRODUCED
        )).stream().map(jobService::view).toList();
    }

    @PatchMapping("/{id}/status")
    public JobDtos.JobView status(@AuthenticationPrincipal AppUser admin, @PathVariable Long id,
                                  @RequestBody Map<String, String> body) {
        Job job = jobs.findById(id).orElseThrow();
        job.setStatus(JobStatus.valueOf(body.get("status")));
        job.touch();
        jobs.save(job);
        audit.save(AuditLog.of(admin.getId(), "JOB_STATUS", "job=" + id + " -> " + job.getStatus()));
        log.info("Admin set job {} status {}", id, job.getStatus());
        return jobService.view(job);
    }
}
