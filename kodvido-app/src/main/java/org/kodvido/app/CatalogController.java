package org.kodvido.app;
import org.kodvido.engine.CatalogService;
import org.kodvido.engine.CatalogOverviewService;
import org.kodvido.engine.ProjectDetailsService;
import org.kodvido.detection.ProjectInspector;
import org.kodvido.domain.CatalogException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.util.*;
@Controller
public class CatalogController {
    private final CatalogService service;
    private final InventoryJobs jobs;
    private final ProjectDetailsService details;
    private final org.kodvido.engine.WorkflowService workflow;
    private final org.kodvido.engine.BackupAssessmentService assessments;
    private final org.kodvido.engine.GuidanceService guidance;
    private final CatalogOverviewService overview;
    public CatalogController(CatalogService service, ProjectDetailsService details, org.kodvido.engine.WorkflowService workflow, org.kodvido.engine.BackupAssessmentService assessments, org.kodvido.engine.GuidanceService guidance,CatalogOverviewService overview,InventoryJobs jobs) {
        this.jobs=jobs;this.service = service; this.details = details; this.workflow = workflow; this.assessments=assessments;this.guidance=guidance;this.overview=overview;
    }
    @GetMapping("/projects/{id}") public String project(@PathVariable("id") UUID id, Model model) {
        model.addAttribute("project", details.project(id)); model.addAttribute("workflow", workflow.load(id));
        model.addAttribute("assessment", assessments.load(id));
        model.addAttribute("interactions", details.interactions(id)); addGuidance(model); return "project";
    }
    @PostMapping("/projects/{id}/inspect") public String inspect(@PathVariable("id") UUID id, Model model) {
        model.addAttribute("project", details.project(id));
        model.addAttribute("workflow", workflow.load(id));
        var observation=details.observe(id);
        model.addAttribute("inspection", observation.files());
        model.addAttribute("git", observation.git());
        model.addAttribute("stack", observation.stack());
        model.addAttribute("assessment", assessments.load(id));
        model.addAttribute("interactions", details.interactions(id));
        model.addAttribute("exclusions", ProjectInspector.EXCLUDED.stream().sorted().toList());
        addGuidance(model);return "project";
    }
    private void addGuidance(Model model) {
        var values=model.asMap();
        var result=guidance.evaluate((org.kodvido.domain.WorkflowNotes)values.get("workflow"),
            (org.kodvido.domain.BackupAssessment)values.get("assessment"),
            (org.kodvido.domain.ProjectInspection)values.get("inspection"),
            (org.kodvido.domain.GitObservation)values.get("git"),(org.kodvido.domain.StackObservation)values.get("stack"));
        model.addAttribute("recommendations",GuidanceView.cards(result));
        model.addAttribute("gapDrafts",GuidanceView.drafts(result,guidance));
    }
    @GetMapping("/projects/{id}/documents/{name}") public String document(@PathVariable("id") UUID id,
            @PathVariable("name") String name, Model model) {
        model.addAttribute("project", details.project(id));
        model.addAttribute("document", details.document(id, name)); return "document";
    }
    @GetMapping("/catalog") public String home(Model model,@RequestParam(value="q",defaultValue="") String q,
            @RequestParam(value="technology",defaultValue="ALL") CatalogOverviewService.Technology technology,
            @RequestParam(value="filter",defaultValue="ALL") CatalogOverviewService.Filter filter,
            @RequestParam(value="sort",defaultValue="NAME") CatalogOverviewService.Sort sort,
            @RequestParam(value="page",defaultValue="0") int page) {
        model.addAttribute("catalog",overview.list(q,technology,filter,sort,page));
        model.addAttribute("q",q);model.addAttribute("technology",technology);model.addAttribute("filter",filter);model.addAttribute("sort",sort);
        return "index";
    }
    @PostMapping("/scan") public String scan(@RequestParam("roots") String roots, Model model) {
        var preview = service.preview(roots.lines().filter(s -> !s.isBlank()).toList());
        model.addAttribute("preview", preview); return "review";
    }
    @PostMapping("/import") public String confirm(@RequestParam("previewId") UUID id,
            @RequestParam(value="selected", required=false) List<Integer> selected) throws IOException {
        service.confirm(id, selected == null ? List.of() : selected); jobs.imported();return "redirect:/";
    }
    @PostMapping("/projects/{id}/relocate") public String relocate(@PathVariable("id") UUID id, @RequestParam("path") String path) throws IOException {
        service.relocate(id, path); return "redirect:/";
    }
    @PostMapping("/projects/{id}/remove") public String remove(@PathVariable("id") UUID id) { service.remove(id); return "redirect:/"; }
    @ExceptionHandler({IllegalArgumentException.class, IOException.class, CatalogException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String failure(Exception ex, Model model) {
        model.addAttribute("message", ex instanceof IOException ? "Dossier absent, lié ou inaccessible." : ex.getMessage()); return "failure";
    }
}
