package com.example.reconciliation.demo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/demo-data")
public class DemoDataController {
    private final DemoDataService service;
    public DemoDataController(DemoDataService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DemoDataService.ImportState start(@RequestParam(defaultValue = "1000000") int count) {
        try { return service.start(count); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }

    @GetMapping
    public DemoDataService.ImportState status() { return service.getState(); }
}
