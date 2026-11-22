package com.example.reconciliation.imports;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

@Tag(name = "Import", description = "CSV upload, scheduler scan, and batch status")
@RestController
@RequestMapping("/api/imports")
public class ImportController {
    private final ImportService service;
    private final ImportRepository batches;
    private final IncomingFileScheduler scheduler;

    public ImportController(ImportService service, ImportRepository batches, IncomingFileScheduler scheduler) {
        this.service = service;
        this.batches = batches;
        this.scheduler = scheduler;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportView upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        String name = safeFileName(file.getOriginalFilename());
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only .csv files are supported");
        }
        Path inbox = service.inbox();
        Files.createDirectories(inbox);
        Path target = inbox.resolve(name);
        Path temporary = inbox.resolve(name + ".uploading");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return ImportView.from(service.registerFile(target));
    }

    @PostMapping("/scan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public int scan() {
        return scheduler.scanOnce();
    }

    @GetMapping
    public List<ImportView> list() {
        return batches.findTop50ByOrderByIdDesc().stream().map(ImportView::from).toList();
    }

    @GetMapping("/{id}")
    public ImportView get(@PathVariable Long id) {
        return batches.findById(id).map(ImportView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import batch not found"));
    }

    private static String safeFileName(String original) {
        String cleaned = StringUtils.cleanPath(original == null ? "upload.csv" : original);
        String name = cleaned.substring(cleaned.lastIndexOf('/') + 1);
        if (name.isBlank()) name = "upload.csv";
        if (name.length() > 200) name = name.substring(name.length() - 200);
        return name;
    }
}
