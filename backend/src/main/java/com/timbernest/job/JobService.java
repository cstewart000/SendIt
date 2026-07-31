package com.timbernest.job;

import tools.jackson.databind.ObjectMapper;
import com.timbernest.common.ApiException;
import com.timbernest.design.DesignAccess;
import com.timbernest.design.DesignPart;
import com.timbernest.design.DesignPartRepository;
import com.timbernest.design.DesignVersion;
import com.timbernest.design.DesignVersionRepository;
import com.timbernest.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private final JobRepository jobs;
    private final JobPartRepository parts;
    private final DesignVersionRepository versions;
    private final DesignPartRepository designParts;
    private final DesignAccess designAccess;
    private final ObjectMapper mapper;

    public JobService(JobRepository jobs, JobPartRepository parts, DesignVersionRepository versions,
                      DesignPartRepository designParts, DesignAccess designAccess, ObjectMapper mapper) {
        this.jobs = jobs; this.parts = parts; this.versions = versions;
        this.designParts = designParts; this.designAccess = designAccess; this.mapper = mapper;
    }

    public JobDtos.JobView create(AppUser user, JobDtos.CreateJobRequest req) {
        Job job = new Job();
        job.setOwnerId(user.getId());
        job.setTitle(cleanTitle(req.title(), null));
        job.setMachineId(req.machineId());
        job.setMaterialId(req.materialId());
        job.setToolId(req.toolId());
        if (req.marginMm() != null) job.setMarginMm(req.marginMm());
        if (req.partGapMm() != null) job.setPartGapMm(req.partGapMm());
        jobs.save(job);
        if (job.getTitle() == null || job.getTitle().isBlank()) {
            job.setTitle("Job #" + job.getId());
            jobs.save(job);
        }
        log.info("Created job id={} title={}", job.getId(), job.getTitle());
        return view(job);
    }

    public JobDtos.JobView update(AppUser user, Long id, JobDtos.UpdateJobRequest req) {
        Job job = owned(user, id);
        job.setTitle(cleanTitle(req.title(), "Job #" + id));
        job.touch();
        jobs.save(job);
        log.info("Updated job {} title={}", id, job.getTitle());
        return view(job);
    }

    private String cleanTitle(String title, String fallback) {
        if (title == null) return fallback;
        String t = title.trim();
        if (t.isEmpty()) return fallback;
        return t.length() > 120 ? t.substring(0, 120) : t;
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
        Map<Long, Integer> qtyByPart = qtyMap(req);
        List<DesignPart> selected = resolveParts(v.getId(), req.partIds(), qtyByPart);
        if (selected.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No nestable parts on this design version");
        }
        int defaultQty = req.quantity() == null ? 1 : Math.max(1, req.quantity());
        boolean grain = Boolean.TRUE.equals(req.grainSensitive());
        for (DesignPart dp : selected) {
            int qty = Math.max(1, qtyByPart.getOrDefault(dp.getId(), defaultQty));
            JobPart part = new JobPart();
            part.setJobId(jobId);
            part.setDesignVersionId(v.getId());
            part.setDesignPartId(dp.getId());
            part.setLabel(req.label() != null && selected.size() == 1 ? req.label() : dp.getLabel());
            part.setQuantity(qty);
            part.setGrainSensitive(grain);
            part.setWidthMm(dp.getWidthMm());
            part.setHeightMm(dp.getHeightMm());
            parts.save(part);
        }
        job.touch();
        jobs.save(job);
        log.info("Added {} design parts to job {}", selected.size(), jobId);
        return view(job);
    }

    public JobDtos.JobView updateQuantities(AppUser user, Long jobId, JobDtos.UpdateQuantitiesRequest req) {
        Job job = owned(user, jobId);
        if (job.isNestingLocked()) throw new ApiException(HttpStatus.CONFLICT, "Nesting locked");
        List<JobPart> jobParts = parts.findByJobId(jobId);
        if (req.allQuantity() != null) {
            int q = Math.max(1, req.allQuantity());
            for (JobPart p : jobParts) {
                p.setQuantity(q);
                parts.save(p);
            }
            log.info("Set all job {} parts to qty {}", jobId, q);
        } else if (req.updates() != null) {
            for (JobDtos.QtyUpdate u : req.updates()) {
                for (JobPart p : jobParts) {
                    if (p.getId().equals(u.jobPartId())) {
                        p.setQuantity(Math.max(1, u.quantity() == null ? 1 : u.quantity()));
                        parts.save(p);
                    }
                }
            }
        }
        job.touch();
        jobs.save(job);
        return view(job);
    }

    private Map<Long, Integer> qtyMap(JobDtos.AddPartRequest req) {
        Map<Long, Integer> map = new java.util.HashMap<>();
        if (req.quantities() != null) {
            for (JobDtos.PartQty q : req.quantities()) {
                if (q.partId() != null) {
                    map.put(q.partId(), Math.max(1, q.quantity() == null ? 1 : q.quantity()));
                }
            }
        }
        return map;
    }

    private List<DesignPart> resolveParts(Long versionId, List<Long> partIds, Map<Long, Integer> qtyByPart) {
        List<DesignPart> all = designParts.findByDesignVersionIdOrderByPartIndexAsc(versionId);
        if (partIds != null && !partIds.isEmpty()) {
            List<DesignPart> out = new ArrayList<>();
            for (DesignPart p : all) {
                if (partIds.contains(p.getId())) out.add(p);
            }
            return out;
        }
        if (!qtyByPart.isEmpty()) {
            List<DesignPart> out = new ArrayList<>();
            for (DesignPart p : all) {
                if (qtyByPart.containsKey(p.getId())) out.add(p);
            }
            return out;
        }
        return all;
    }

    public Job owned(AppUser user, Long id) {
        Job job = jobs.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!job.getOwnerId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not your job");
        }
        return job;
    }

    public JobDtos.JobView view(Job job) {
        String title = job.getTitle() == null || job.getTitle().isBlank()
                ? "Job #" + job.getId() : job.getTitle();
        List<JobPart> jobParts = parts.findByJobId(job.getId());
        Map<String, Object> nesting = readMap(job.getNestingJson());
        Map<String, Object> quote = job.getQuoteJson() == null ? null : readMap(job.getQuoteJson());
        JobDtos.JobSummary summary = summarize(jobParts, nesting, quote);
        log.info("Job {} summary parts={} sheets={} areaMm2={} cost={}",
                job.getId(), summary.partCount(), summary.sheetCount(),
                Math.round(summary.partsAreaMm2()), summary.cost());
        return new JobDtos.JobView(job.getId(), title, job.getStatus(), job.getMachineId(),
                job.getMaterialId(), job.getToolId(), job.isNestingLocked(), job.getMarginMm(),
                job.getPartGapMm(), jobParts, nesting, quote,
                job.getGcodePath() != null, summary);
    }

    private JobDtos.JobSummary summarize(List<JobPart> jobParts, Map<String, Object> nesting,
                                         Map<String, Object> quote) {
        int partCount = jobParts.stream().mapToInt(JobPart::getQuantity).sum();
        double area = jobParts.stream()
                .mapToDouble(p -> p.getWidthMm() * p.getHeightMm() * p.getQuantity()).sum();
        int sheets = nesting.get("sheetCount") instanceof Number n ? n.intValue() : 0;
        Double cycle = null;
        Double cost = null;
        String currency = null;
        if (quote != null) {
            if (quote.get("cycleMinutes") instanceof Number n) cycle = n.doubleValue();
            if (quote.get("total") instanceof Number n) cost = n.doubleValue();
            if (quote.get("currency") instanceof String s) currency = s;
        }
        return new JobDtos.JobSummary(partCount, sheets, Math.round(area * 100.0) / 100.0,
                cycle, cost, currency);
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
