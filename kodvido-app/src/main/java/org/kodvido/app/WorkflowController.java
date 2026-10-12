// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.app;

import org.kodvido.domain.*;
import org.kodvido.engine.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Controller
public class WorkflowController {
    private final WorkflowService service;
    private final ProjectDetailsService projects;
    public WorkflowController(WorkflowService service, ProjectDetailsService projects) { this.service = service; this.projects = projects; }
    @GetMapping("/projects/{id}/workflow") public String form(@PathVariable("id") UUID id, Model model) {
        var notes = service.load(id);
        var slots = new ArrayList<>(notes.phases());
        while (slots.size() < 5) slots.add(new WorkflowNotes.Phase(null, "", WorkflowNotes.State.PLANNED));
        model.addAttribute("project", projects.project(id)); model.addAttribute("notes", notes); model.addAttribute("slots", slots);
        return "workflow";
    }
    @PostMapping("/projects/{id}/workflow") public String save(@PathVariable("id") UUID id,
            @RequestParam("revision") long revision, @RequestParam("date") List<String> dates,
            @RequestParam("description") List<String> descriptions, @RequestParam("state") List<String> states,
            @RequestParam("development") String development, @RequestParam("operations") String operations) {
        if (dates.size() != 5 || descriptions.size() != 5 || states.size() != 5) throw new IllegalArgumentException("Fiche incomplète.");
        var phases = new ArrayList<WorkflowNotes.Phase>();
        for (int i = 0; i < 5; i++) {
            if (dates.get(i).isBlank() && descriptions.get(i).isBlank()) continue;
            try { phases.add(new WorkflowNotes.Phase(LocalDate.parse(dates.get(i)), descriptions.get(i), WorkflowNotes.State.valueOf(states.get(i)))); }
            catch (DateTimeException | IllegalArgumentException ex) { throw new IllegalArgumentException("Date ou état de phase invalide."); }
        }
        service.save(id, revision, phases, development, operations);
        return "redirect:/projects/" + id;
    }
    @ExceptionHandler(WorkflowConflictException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Exception ex, jakarta.servlet.http.HttpServletRequest request, Model model) {
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("development", request.getParameter("development"));
        model.addAttribute("operations", request.getParameter("operations"));
        model.addAttribute("dates", request.getParameterValues("date"));
        model.addAttribute("descriptions", request.getParameterValues("description"));
        model.addAttribute("states", request.getParameterValues("state"));
        model.addAttribute("returnUrl", request.getRequestURI());
        return "workflow-conflict";
    }
    @ExceptionHandler({IllegalArgumentException.class, CatalogException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String failure(Exception ex, Model model) { model.addAttribute("message", ex.getMessage()); return "failure"; }
}
