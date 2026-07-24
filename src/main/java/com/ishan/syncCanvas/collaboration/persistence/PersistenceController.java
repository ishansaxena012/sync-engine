package com.ishan.syncCanvas.collaboration.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PersistenceController {

    private final PersistenceMetrics metrics;

    @GetMapping("/api/persistence/metrics")
    public PersistenceMetrics metrics() {

        return metrics;

    }

}