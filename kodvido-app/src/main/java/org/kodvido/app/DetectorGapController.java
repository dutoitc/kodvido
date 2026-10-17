// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.app;
import org.kodvido.domain.ProjectGuidance.*;
import org.kodvido.domain.ProjectInspection.Status;
import org.kodvido.engine.GuidanceService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.nio.charset.StandardCharsets;

@RestController
public class DetectorGapController {
    private final GuidanceService service;
    public DetectorGapController(GuidanceService service) { this.service=service; }
    @PostMapping("/detector-gap/draft") public ResponseEntity<byte[]> draft(
            @RequestParam("detector") Detector detector,@RequestParam("status") Status status) {
        return ResponseEntity.ok().contentType(new MediaType("text","markdown",StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=kodvido-detector-gap.md")
            .cacheControl(CacheControl.noStore()).body(service.draft(new Gap(detector,status)).getBytes(StandardCharsets.UTF_8));
    }
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<String> invalid() { return ResponseEntity.badRequest().body("Détecteur ou état non pris en charge."); }
}
