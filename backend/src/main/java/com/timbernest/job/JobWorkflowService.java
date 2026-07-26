package com.timbernest.job;

import com.timbernest.admin.*;
import com.timbernest.cam.GCodeGenerator;
import com.timbernest.cam.SetupSheetWriter;
import com.timbernest.common.ApiException;
import com.timbernest.common.JobStatus;
import com.timbernest.design.DesignAccess;
import com.timbernest.design.DesignPart;
import com.timbernest.design.DesignPartRepository;
import com.timbernest.design.DesignVersion;
import com.timbernest.design.DesignVersionRepository;
import com.timbernest.geometry.JsonUtil;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.nesting.NestPlacement;
import com.timbernest.nesting.NestResult;
import com.timbernest.nesting.NestingService;
import com.timbernest.quote.QuoteService;
import com.timbernest.storage.FileStorageService;
import com.timbernest.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.util.*;

@Service
public class JobWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(JobWorkflowService.class);
    private final JobService jobs;
    private final JobRepository jobRepo;
    private final JobPartRepository parts;
    private final MachineRepository machines;
    private final MaterialRepository materials;
    private final ToolRepository tools;
    private final PricingRuleRepository pricing;
    private final DesignVersionRepository versions;
    private final DesignPartRepository designParts;
    private final DesignAccess designAccess;
    private final JsonUtil json;
    private final NestingService nesting;
    private final QuoteService quotes;
    private final GCodeGenerator gcode;
    private final SetupSheetWriter setupSheets;
    private final FileStorageService storage;

    public JobWorkflowService(JobService jobs, JobRepository jobRepo, JobPartRepository parts,
                              MachineRepository machines, MaterialRepository materials,
                              ToolRepository tools, PricingRuleRepository pricing,
                              DesignVersionRepository versions, DesignPartRepository designParts,
                              DesignAccess designAccess, JsonUtil json, NestingService nesting,
                              QuoteService quotes, GCodeGenerator gcode,
                              SetupSheetWriter setupSheets, FileStorageService storage) {
        this.jobs = jobs; this.jobRepo = jobRepo; this.parts = parts; this.machines = machines;
        this.materials = materials; this.tools = tools; this.pricing = pricing;
        this.versions = versions; this.designParts = designParts; this.designAccess = designAccess;
        this.json = json; this.nesting = nesting; this.quotes = quotes; this.gcode = gcode;
        this.setupSheets = setupSheets; this.storage = storage;
    }

    public JobDtos.JobView nest(AppUser user, Long jobId) {
        Job job = jobs.owned(user, jobId);
        Material mat = materials.findById(job.getMaterialId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Material missing"));
        NestResult result = nesting.nest(parts.findByJobId(jobId), mat.getSheetWidthMm(),
                mat.getSheetHeightMm(), job.getMarginMm(), job.getPartGapMm());
        job.setNestingJson(jobs.writeJson(result));
        job.setStatus(JobStatus.NESTED);
        job.setNestingLocked(false);
        job.touch();
        jobRepo.save(job);
        log.info("Nested job {}", jobId);
        return jobs.view(job);
    }

    public JobDtos.JobView adjust(AppUser user, Long jobId, List<NestPlacement> placements) {
        Job job = jobs.owned(user, jobId);
        if (job.isNestingLocked()) throw new ApiException(HttpStatus.CONFLICT, "Nesting locked");
        Map<Long, JobPart> byId = new HashMap<>();
        for (JobPart p : parts.findByJobId(jobId)) byId.put(p.getId(), p);
        nesting.syncSharedOrientations(placements, byId);
        NestResult nest = readNest(job);
        nest.setPlacements(placements);
        job.setNestingJson(jobs.writeJson(nest));
        job.touch();
        jobRepo.save(job);
        log.info("Adjusted nesting for job {} ({} placements)", jobId, placements.size());
        return jobs.view(job);
    }

    public JobDtos.JobView lock(AppUser user, Long jobId) {
        Job job = jobs.owned(user, jobId);
        job.setNestingLocked(true);
        job.setStatus(JobStatus.NESTED);
        job.touch();
        jobRepo.save(job);
        return jobs.view(job);
    }

    public JobDtos.JobView quote(AppUser user, Long jobId) {
        Job job = jobs.owned(user, jobId);
        if (!job.isNestingLocked()) throw new ApiException(HttpStatus.BAD_REQUEST, "Lock nesting first");
        NestResult nest = readNest(job);
        Machine machine = machines.findById(job.getMachineId()).orElseThrow();
        Material mat = materials.findById(job.getMaterialId()).orElseThrow();
        Tool tool = tools.findById(job.getToolId()).orElseThrow();
        List<GeometryModel> geos = geometries(user, jobId);
        Map<String, Object> q = quotes.quote(nest, geos, machine, mat, tool, pricing);
        job.setQuoteJson(jobs.writeJson(q));
        job.setStatus(JobStatus.QUOTED);
        job.touch();
        jobRepo.save(job);
        return jobs.view(job);
    }

    public JobDtos.JobView approve(AppUser user, Long jobId) {
        Job job = jobs.owned(user, jobId);
        if (job.getQuoteJson() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Quote first");
        NestResult nest = readNest(job);
        Machine machine = machines.findById(job.getMachineId()).orElseThrow();
        Material mat = materials.findById(job.getMaterialId()).orElseThrow();
        Tool tool = tools.findById(job.getToolId()).orElseThrow();
        List<GeometryModel> geos = geometries(user, jobId);
        String nc = gcode.generate(nest, geos, nest.getPlacements(), machine, tool, mat);
        String setup = setupSheets.write(jobId, machine, tool, mat, nest, jobs.readMap(job.getQuoteJson()));
        job.setGcodePath(storage.writeText("gcode", "job-" + jobId + ".ngc", nc));
        job.setSetupSheetPath(storage.writeText("gcode", "job-" + jobId + "-setup.txt", setup));
        job.setStatus(JobStatus.READY_FOR_PRODUCTION);
        job.touch();
        jobRepo.save(job);
        log.info("Approved job {} -> READY_FOR_PRODUCTION", jobId);
        return jobs.view(job);
    }

    public byte[] download(AppUser user, Long jobId, boolean setup) throws Exception {
        Job job = jobs.owned(user, jobId);
        String path = setup ? job.getSetupSheetPath() : job.getGcodePath();
        if (path == null) throw new ApiException(HttpStatus.NOT_FOUND, "Artifact not ready");
        return Files.readAllBytes(storage.resolve(path));
    }

    private List<GeometryModel> geometries(AppUser user, Long jobId) {
        List<GeometryModel> list = new ArrayList<>();
        for (JobPart p : parts.findByJobId(jobId)) {
            DesignVersion v = versions.findById(p.getDesignVersionId()).orElseThrow();
            designAccess.ownedVersion(user, v.getDesignId(), v.getId());
            GeometryModel model;
            if (p.getDesignPartId() != null) {
                DesignPart dp = designParts.findById(p.getDesignPartId())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Design part missing"));
                model = json.toModel(dp.getGeometryJson());
            } else {
                model = designAccess.loadOrParse(v);
            }
            for (int i = 0; i < p.getQuantity(); i++) list.add(model);
        }
        return list;
    }

    private NestResult readNest(Job job) {
        try {
            return new tools.jackson.databind.json.JsonMapper()
                    .readValue(job.getNestingJson(), NestResult.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid nesting data");
        }
    }
}
