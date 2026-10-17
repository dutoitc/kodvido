// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.app;
import org.kodvido.domain.*;
import org.kodvido.domain.Inventory.*;
import org.kodvido.engine.InventoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
@Controller
public final class InventoryController {
    private final InventoryService service;private final InventoryJobs jobs;
    public InventoryController(InventoryService service,InventoryJobs jobs) {this.service=service;this.jobs=jobs;}
    @GetMapping("/") public String dashboard(Model m,
        @RequestParam(value="q",defaultValue="") String q,@RequestParam(value="lifecycle",defaultValue="") String lifecycle,
        @RequestParam(value="technology",defaultValue="") String technology,@RequestParam(value="remote",defaultValue="") String remote,
        @RequestParam(value="sort",defaultValue="COMMIT") InventoryService.Sort sort,@RequestParam(value="descending",defaultValue="true") boolean descending,
        @RequestParam(value="page",defaultValue="0") int page) {
        if(page<0) throw new IllegalArgumentException("Page invalide.");
        var rows=service.rows(q,lifecycle,technology,remote,sort,descending);int pages=Math.max(1,(rows.size()+49)/50);page=Math.min(page,pages-1);
        m.addAttribute("rows",rows.subList(page*50,Math.min(rows.size(),(page+1)*50)));m.addAttribute("count",rows.size());m.addAttribute("page",page);m.addAttribute("pages",pages);
        m.addAttribute("q",q);m.addAttribute("lifecycle",lifecycle);m.addAttribute("technology",technology);m.addAttribute("remote",remote);m.addAttribute("sort",sort);m.addAttribute("descending",descending);
        m.addAttribute("roots",String.join("\n",service.roots()));m.addAttribute("discovery",service.discovery());m.addAttribute("job",jobs.state());
        m.addAttribute("lifecycles",Lifecycle.values());m.addAttribute("remoteStates",RemoteState.values());m.addAttribute("sorts",InventoryService.Sort.values());return "dashboard";
    }
    @GetMapping("/inventory/{id}") public String detail(@PathVariable("id") UUID id,Model m) {
        m.addAttribute("row",service.row(id));m.addAttribute("lifecycles",Lifecycle.values());m.addAttribute("job",jobs.state());return "inventory-project";
    }
    @PostMapping("/inventory/roots") public String roots(@RequestParam("roots") String roots) {
        service.roots(roots.lines().filter(s->!s.isBlank()).toList());jobs.submit(null,false);return "redirect:/";
    }
    @PostMapping("/inventory/refresh") public String refresh() {return jobs.submit(null,false)?"redirect:/?queued":"redirect:/?busy";}
    @PostMapping("/inventory/{id}/refresh") public String refresh(@PathVariable("id") UUID id,@RequestParam(value="dependencies",defaultValue="false") boolean dependencies) {
        service.row(id);return "redirect:/inventory/"+id+(jobs.submit(id,dependencies)?"?queued":"?busy");
    }
    @PostMapping("/inventory/{id}/notes") public String notes(@PathVariable("id") UUID id,@RequestParam("lifecycle") Lifecycle lifecycle,
        @RequestParam("note") String note,@RequestParam("tasks") String tasks,@RequestParam("revision") long revision,Model m) {
        try {service.notes(id,lifecycle,note,tasks,revision);} catch(IllegalArgumentException e) {
            m.addAttribute("row",service.row(id));m.addAttribute("lifecycles",Lifecycle.values());m.addAttribute("job",jobs.state());
            m.addAttribute("submitted",new Notes(lifecycle,note,tasks,revision));m.addAttribute("error",e.getMessage());return "inventory-project";
        }return "redirect:/inventory/"+id;
    }
    @ExceptionHandler({IllegalArgumentException.class,CatalogException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String failure(Exception e,Model m) {m.addAttribute("message",e.getMessage());return "failure";}
}
