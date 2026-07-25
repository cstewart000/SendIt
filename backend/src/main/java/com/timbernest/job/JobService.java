package com.timbernest.job;

import tools.jackson.databind.ObjectMapper;
import com.timbernest.common.ApiException;
import com.timbernest.design.DesignAccess;
import com.timbernest.design.DesignVersion;
import com.timbernest.design.DesignVersionRepository;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private final JobRepository jobs;
    private final JobPartRepository parts;
    private final DesignVersionRepository versions;
    private final DesignAccess designAccess;
    private final ObjectMapper mapper;

    public JobService(JobRepository jobs, JobPartRepository parts, DesignVersionRepository versions,
                      DesignAccess designAccess, ObjectMapper mapper) {
        this.jobs = jobs;
        this.parts = parts;
        this.versions = versions;
        this.designAccess = designAccess;
        this.mapper = mapper;
    }

    public JobDtos.JobView create(AppUser user, JobDtos.CreateJobRequest req) {
        Job job = new Job();
        job.setOwnerId(user.getId());
        job.setMachineId(req.machineId());
        job.setMaterialId(req.materialId());
        job.setToolId(req.toolId());
        if (req.marginMm() != null) job.setMarginMm(req.marginMm());
        if (req.partGapMm() != null) job.setPartGapMm(req.partGapMm());
        jobs.save(job);
        log.info("Created job id={}", job.getId());
        return view(job);
    }

    public List<JobDtos.JobView> listMine(AppUser user) {
        return jobs.findByOwnerIdOrderByUpdatedAtDesc(user.getId()).stream().map(this::view).toList();
    }

    public JobDtos.JobView get(AppUser user, Long id) {
        return view(owned(user, id));
    }

    public JobDtos.JobView addPart(AppUser user, Long jobId, JobDtos.AddPartRequest req) {
        Job job = owned(user, jobId);
        if (job.isNestingLocked()) throw new ApiException(HttpStatus.CONFLICT, "Nesting locked");
        DesignVersion v = versions.findById(req.designVersionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Version not found"));
        designAccess.ownedVersion(user, v.getDesignId(), v.getId());
        GeometryModel model = designAccess.loadOrParse(v);
        JobPart part = new JobPart();
        part.setJobId(jobId);
        part.setDesignVersionId(v.getId());
        part.setLabel(req.label() == null ? "Part" : req.label());
        part.setQuantity(req.quantity() == null ? 1 : req.quantity());
        part.setGrainSensitive(Boolean.TRUE.equals(req.grainSensitive()));
        part.setWidthMm(Math.max(1, model.width()));
        part.setHeightMm(Math.max(1, model.height()));
        parts.save(part);
        job.touch();
        jobs.save(job);
        return view(job);
    }

    public Job owned(AppUser user, Long id) {
        Job job = jobs.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!job.getOwnerId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not your job");
        }
        return job;
    }

    public JobDtos.JobView view(Job job) {
        return new JobDtos.JobView(job.getId(), job.getStatus(), job.getMachineId(), job.getMaterialId(),
                job.getToolId(), job.isNestingLocked(), job.getMarginMm(), job.getPartGapMm(),
                parts.findByJobId(job.getId()), readMap(job.getNestingJson()),
                job.getQuoteJson() == null ? null : readMap(job.getQuoteJson()),
                job.getGcodePath() != null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> readMap(String json) {
        try { return mapper.readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    public String writeJson(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage()); }
    }
}
