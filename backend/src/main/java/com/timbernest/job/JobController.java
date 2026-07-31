package com.timbernest.job;

import com.timbernest.cam.CamOptions;
import com.timbernest.cam.ToolpathResult;
import com.timbernest.user.AppUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private final JobService jobs;
    private final JobWorkflowService workflow;

    public JobController(JobService jobs, JobWorkflowService workflow) {
        this.jobs = jobs;
        this.workflow = workflow;
    }

    @GetMapping
    public List<JobDtos.JobView> list(@AuthenticationPrincipal AppUser user) {
        return jobs.listMine(user);
    }

    @PostMapping
    public JobDtos.JobView create(@AuthenticationPrincipal AppUser user,
                                  @RequestBody JobDtos.CreateJobRequest req) {
        return jobs.create(user, req);
    }

    @GetMapping("/{id}")
    public JobDtos.JobView get(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return jobs.get(user, id);
    }

    @PatchMapping("/{id}")
    public JobDtos.JobView update(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                  @RequestBody JobDtos.UpdateJobRequest req) {
        return jobs.update(user, id, req);
    }

    @GetMapping("/{id}/nest-shapes")
    public List<JobDtos.NestShape> nestShapes(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return workflow.nestShapes(user, id);
    }

    @PostMapping("/{id}/parts")
    public JobDtos.JobView addPart(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                   @RequestBody JobDtos.AddPartRequest req) {
        return jobs.addPart(user, id, req);
    }

    @PatchMapping("/{id}/parts")
    public JobDtos.JobView updateQuantities(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                            @RequestBody JobDtos.UpdateQuantitiesRequest req) {
        return jobs.updateQuantities(user, id, req);
    }

    @PostMapping("/{id}/nest")
    public JobDtos.JobView nest(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return workflow.nest(user, id);
    }

    @PatchMapping("/{id}/nesting")
    public JobDtos.JobView adjust(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                  @RequestBody JobDtos.AdjustNestRequest req) {
        return workflow.adjust(user, id, req.placements());
    }

    @PostMapping("/{id}/lock-nesting")
    public JobDtos.JobView lock(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return workflow.lock(user, id);
    }

    @PostMapping("/{id}/quote")
    public JobDtos.JobView quote(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return workflow.quote(user, id);
    }

    @PostMapping("/{id}/approve")
    public JobDtos.JobView approve(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return workflow.approve(user, id);
    }

    /** Structured toolpaths for canvas visualisation (available after nest). */
    @GetMapping("/{id}/toolpath")
    public ToolpathResult toolpath(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return workflow.toolpath(user, id);
    }

    /** Toggle screw fixings / tabs; body: { disabledFixingIds, tabsEnabled, fixingsEnabled }. */
    @PatchMapping("/{id}/cam-options")
    public ToolpathResult camOptions(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                     @RequestBody CamOptions options) {
        return workflow.updateCamOptions(user, id, options);
    }

    @GetMapping("/{id}/gcode")
    public ResponseEntity<byte[]> gcode(@AuthenticationPrincipal AppUser user, @PathVariable Long id)
            throws Exception {
        return file(workflow.download(user, id, false), "job-" + id + ".ngc", "text/plain");
    }

    @GetMapping("/{id}/setup-sheet")
    public ResponseEntity<byte[]> setup(@AuthenticationPrincipal AppUser user, @PathVariable Long id)
            throws Exception {
        return file(workflow.download(user, id, true), "job-" + id + "-setup.txt", "text/plain");
    }

    private ResponseEntity<byte[]> file(byte[] body, String name, String type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType(type))
                .body(body);
    }
}
