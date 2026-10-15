// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.app;
import org.kodvido.domain.*;
import org.kodvido.domain.BackupAssessment.*;
import org.kodvido.engine.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;

@Controller
public class BackupAssessmentController {
    private final BackupAssessmentService service;
    private final ProjectDetailsService projects;
    public BackupAssessmentController(BackupAssessmentService service,ProjectDetailsService projects) { this.service=service;this.projects=projects; }
    @GetMapping("/projects/{id}/backup-evidence") public String form(@PathVariable("id") UUID id,Model model) {
        model.addAttribute("assessment",service.load(id));model.addAttribute("project",projects.project(id));return "backup-evidence";
    }
    @PostMapping("/projects/{id}/backup-evidence") public String save(@PathVariable("id") UUID id,
            @RequestParam("revision") long revision,HttpServletRequest request) {
        var items=new ArrayList<Item>();
        for(var dimension:Dimension.values()) {
            String prefix=dimension.name();
            String state=single(request,prefix+".state"), date=single(request,prefix+".date"), evidence=single(request,prefix+".evidence");
            try { items.add(new Item(dimension,State.valueOf(state),date.isBlank()?null:LocalDate.parse(date),evidence)); }
            catch(DateTimeException | IllegalArgumentException ex) { throw new IllegalArgumentException("Date ou état invalide."); }
        }
        service.save(id,revision,items); return "redirect:/projects/"+id;
    }
    private static String single(HttpServletRequest request,String name) {
        var values=request.getParameterValues(name);
        if(values==null || values.length!=1) throw new IllegalArgumentException("Fiche incomplète ou champ dupliqué.");
        return values[0];
    }
    @ExceptionHandler(BackupAssessmentConflictException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Exception ex,HttpServletRequest request,Model model) {
        model.addAttribute("message",ex.getMessage());model.addAttribute("returnUrl",request.getRequestURI());
        var submitted=new ArrayList<List<String>>();
        for(var d:Dimension.values()) submitted.add(List.of(d.name(),request.getParameter(d+".state"),request.getParameter(d+".date"),request.getParameter(d+".evidence")));
        model.addAttribute("submitted",submitted); return "backup-evidence-conflict";
    }
    @ExceptionHandler({IllegalArgumentException.class,CatalogException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String failure(Exception ex,Model model) { model.addAttribute("message",ex.getMessage());return "failure"; }
}
