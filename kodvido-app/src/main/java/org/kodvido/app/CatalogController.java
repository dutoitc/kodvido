package org.kodvido.app;
import org.kodvido.engine.CatalogService;
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
    public CatalogController(CatalogService service) { this.service = service; }
    @GetMapping("/") public String home(Model model) { model.addAttribute("projects", service.list()); return "index"; }
    @PostMapping("/scan") public String scan(@RequestParam("roots") String roots, Model model) {
        var preview = service.preview(roots.lines().filter(s -> !s.isBlank()).toList());
        model.addAttribute("preview", preview); return "review";
    }
    @PostMapping("/import") public String confirm(@RequestParam("previewId") UUID id,
            @RequestParam(value="selected", required=false) List<Integer> selected) throws IOException {
        service.confirm(id, selected == null ? List.of() : selected); return "redirect:/";
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
