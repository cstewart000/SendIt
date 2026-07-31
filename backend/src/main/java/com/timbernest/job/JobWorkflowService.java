package com.timbernest.job;

import com.timbernest.admin.*;
import com.timbernest.cam.CamOptions;
import com.timbernest.cam.GCodeGenerator;
import com.timbernest.cam.SetupSheetWriter;
import com.timbernest.cam.ToolpathResult;
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
import tools.jackson.databind.ObjectMapper;

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
    private final ObjectMapper mapper;

    public JobWorkflowService(JobService jobs, JobRepository jobRepo, JobPartRepository parts,
                              MachineRepository machines, MaterialRepository materials,
                              ToolRepository tools, PricingRuleRepository pricing,
                              DesignVersionRepository versions, DesignPartRepository designParts,
                              DesignAccess designAccess, JsonUtil json, NestingService nesting,
                              QuoteService quotes, GCodeGenerator gcode,
                              SetupSheetWriter setupSheets, FileStorageService storage,
                              ObjectMapper mapper) {
        this.jobs = jobs; this.jobRepo = jobRepo; this.parts = parts; this.machines = machines;
        this.materials = materials; this.tools = tools; this.pricing = pricing;
        this.versions = versions; this.designParts = designParts; this.designAccess = designAccess;
        this.json = json; this.nesting = nesting; this.quotes = quotes; this.gcode = gcode;
        this.setupSheets = setupSheets; this.storage = storage; this.mapper = mapper;
    }

    public JobDtos.JobView nest(AppUser user, Long jobId) {
        Job job = jobs.owned(user, jobId);
        Material mat = materials.findById(job.getMaterialId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Material missing"));
        Machine machine = machines.findById(job.getMachineId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Machine missing"));
        Tool tool = tools.findById(job.getToolId()).orElse(null);
        // Kerf = min edge margin and min part-to-part gap (at least tool diameter if larger)
        double kerf = machine.getKerfMm() > 0 ? machine.getKerfMm() : 8;
        if (tool != null && tool.getDiameterMm() > kerf) kerf = tool.getDiameterMm();
        double margin = Math.max(job.getMarginMm(), kerf);
        double gap = Math.max(job.getPartGapMm(), kerf);
        job.setMarginMm(margin);
        job.setPartGapMm(gap);

        List<JobPart> jobParts = parts.findByJobId(jobId);
        Map<Long, GeometryModel> geos = new HashMap<>();
        for (JobPart p : jobParts) geos.put(p.getId(), loadModel(user, p));
        NestResult result = nesting.nest(jobParts, geos, mat.getSheetWidthMm(),
                mat.getSheetHeightMm(), margin, gap);
        job.setNestingJson(jobs.writeJson(result));
        job.setStatus(JobStatus.NESTED);
        job.setNestingLocked(false);
        job.touch();
        jobRepo.save(job);
        log.info("Nested job {} kerf/margin={} gap={}", jobId, margin, gap);
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
        ToolpathResult tp = gcode.build(nest, geos, machine, tool, mat, readCam(job));
        String setup = setupSheets.write(jobId, machine, tool, mat, nest, jobs.readMap(job.getQuoteJson()));
        job.setGcodePath(storage.writeText("gcode", "job-" + jobId + ".ngc", tp.getGcode()));
        job.setSetupSheetPath(storage.writeText("gcode", "job-" + jobId + "-setup.txt", setup));
        job.setStatus(JobStatus.READY_FOR_PRODUCTION);
        job.touch();
        jobRepo.save(job);
        log.info("Approved job {} -> READY_FOR_PRODUCTION paths={} fixings={}",
                jobId, tp.getPaths().size(), tp.getFixings().size());
        return jobs.view(job);
    }

    /** Toolpath preview (after nest) or regenerated from saved nest + tool. */
    public ToolpathResult toolpath(AppUser user, Long jobId) {
        Job job = jobs.owned(user, jobId);
        if (job.getNestingJson() == null || job.getNestingJson().isBlank()
                || "{}".equals(job.getNestingJson().trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nest the job before previewing toolpaths");
        }
        NestResult nest = readNest(job);
        if (nest.getPlacements() == null || nest.getPlacements().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No nest placements");
        }
        Machine machine = machines.findById(job.getMachineId()).orElseThrow();
        Material mat = materials.findById(job.getMaterialId()).orElseThrow();
        Tool tool = tools.findById(job.getToolId()).orElseThrow();
        List<GeometryModel> geos = geometries(user, jobId);
        return gcode.build(nest, geos, machine, tool, mat, readCam(job));
    }

    /** Update disabled screw ids / tab toggles; returns refreshed toolpath. */
    public ToolpathResult updateCamOptions(AppUser user, Long jobId, CamOptions options) {
        Job job = jobs.owned(user, jobId);
        if (options == null) options = new CamOptions();
        CamOptions cur = readCam(job);
        if (options.getDisabledFixingIds() != null) {
            cur.setDisabledFixingIds(options.getDisabledFixingIds());
        }
        if (options.getTabsEnabled() != null) cur.setTabsEnabled(options.getTabsEnabled());
        if (options.getFixingsEnabled() != null) cur.setFixingsEnabled(options.getFixingsEnabled());
        try {
            job.setCamJson(mapper.writeValueAsString(cur));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save CAM options");
        }
        job.touch();
        jobRepo.save(job);
        log.info("CAM options job {} disabledFixings={}", jobId, cur.getDisabledFixingIds().size());
        return toolpath(user, jobId);
    }

    private CamOptions readCam(Job job) {
        try {
            if (job.getCamJson() == null || job.getCamJson().isBlank()) return new CamOptions();
            CamOptions o = mapper.readValue(job.getCamJson(), CamOptions.class);
            return o != null ? o : new CamOptions();
        } catch (Exception e) {
            return new CamOptions();
        }
    }

    public byte[] download(AppUser user, Long jobId, boolean setup) throws Exception {
        Job job = jobs.owned(user, jobId);
        String path = setup ? job.getSetupSheetPath() : job.getGcodePath();
        if (path == null) throw new ApiException(HttpStatus.NOT_FOUND, "Artifact not ready");
        return Files.readAllBytes(storage.resolve(path));
    }

    /** Localized outlines for nest canvas (one per job part type). */
    public List<JobDtos.NestShape> nestShapes(AppUser user, Long jobId) {
        jobs.owned(user, jobId);
        List<JobDtos.NestShape> out = new ArrayList<>();
        for (JobPart p : parts.findByJobId(jobId)) {
            GeometryModel model = loadModel(user, p);
            out.add(toNestShape(p, model));
        }
        log.info("Nest shapes for job {}: {}", jobId, out.size());
        return out;
    }

    private List<GeometryModel> geometries(AppUser user, Long jobId) {
        List<GeometryModel> list = new ArrayList<>();
        for (JobPart p : parts.findByJobId(jobId)) {
            GeometryModel model = loadModel(user, p);
            for (int i = 0; i < p.getQuantity(); i++) list.add(model);
        }
        return list;
    }

    private GeometryModel loadModel(AppUser user, JobPart p) {
        DesignVersion v = versions.findById(p.getDesignVersionId()).orElseThrow();
        designAccess.ownedVersion(user, v.getDesignId(), v.getId());
        if (p.getDesignPartId() != null) {
            DesignPart dp = designParts.findById(p.getDesignPartId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Design part missing"));
            return json.toModel(dp.getGeometryJson());
        }
        return designAccess.loadOrParse(v);
    }

    private JobDtos.NestShape toNestShape(JobPart p, GeometryModel model) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        for (var c : model.getContours()) {
            for (var pt : c.getPoints()) {
                minX = Math.min(minX, pt.x());
                minY = Math.min(minY, pt.y());
            }
        }
        if (!Double.isFinite(minX)) { minX = 0; minY = 0; }
        List<JobDtos.ContourPath> paths = new ArrayList<>();
        for (var c : model.getContours()) {
            List<JobDtos.Pt> pts = new ArrayList<>();
            for (var pt : c.getPoints()) {
                pts.add(new JobDtos.Pt(pt.x() - minX, pt.y() - minY));
            }
            if (!pts.isEmpty()) paths.add(new JobDtos.ContourPath(c.isClosed(), pts));
        }
        return new JobDtos.NestShape(p.getId(), p.getWidthMm(), p.getHeightMm(), paths);
    }

    private NestResult readNest(Job job) {
        try {
            return mapper.readValue(job.getNestingJson(), NestResult.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid nesting data");
        }
    }
}
