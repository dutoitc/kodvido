// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.app;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import org.kodvido.domain.*;
import org.kodvido.engine.BackupService;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
public final class BackupController {
    private static final int MAX_BYTES=8*1024*1024;
    private final BackupService service;
    private final ObjectMapper json;
    public BackupController(BackupService service,ObjectMapper mapper) {
        this.service=service; this.json=mapper.copy();
        json.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
        json.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        json.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(16000).maxNumberLength(30).build());
    }
    @GetMapping("/backup") String page() { return "backup"; }
    @PostMapping("/backup/export") ResponseEntity<byte[]> export() throws IOException {
        byte[] bytes=json.writerWithDefaultPrettyPrinter().writeValueAsBytes(service.export());
        if(bytes.length>MAX_BYTES) throw new IllegalArgumentException();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=kodvido-catalog.json")
            .cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(bytes);
    }
    @PostMapping("/backup/preview") String preview(@RequestParam("file") MultipartFile file,Model model) throws IOException {
        if(file.getSize()>MAX_BYTES) throw new IllegalArgumentException();
        try(var input=file.getInputStream()) {
            byte[] bytes=input.readNBytes(MAX_BYTES+1);
            if(bytes.length>MAX_BYTES) throw new IllegalArgumentException();
            var preview=service.preview(json.readValue(bytes,CatalogBackup.class));
            model.addAttribute("preview",preview); return "backup-preview";
        }
    }
    @PostMapping("/backup/confirm") String confirm(@RequestParam("token") UUID token,HttpServletRequest request) {
        String[] paths=request.getParameterValues("path");
        service.confirm(token,paths==null ? List.of() : List.of(paths)); return "redirect:/";
    }
    @ExceptionHandler({IllegalArgumentException.class,NullPointerException.class,IOException.class,java.time.DateTimeException.class,CatalogException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String invalid(Model model) {
        model.addAttribute("message","Sauvegarde invalide, aperçu expiré ou conflit avec le catalogue. Aucun import partiel. Revenez à la sauvegarde pour réessayer.");
        return "failure";
    }
}
