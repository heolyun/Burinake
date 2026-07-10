package com.burinake.controller;

import com.burinake.dto.cctv.CctvRequest;
import com.burinake.dto.cctv.CctvResponse;
import com.burinake.service.CctvManagementService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CctvController {

    private final CctvManagementService cctvManagementService;

    public CctvController(CctvManagementService cctvManagementService) {
        this.cctvManagementService = cctvManagementService;
    }

    @GetMapping("/api/v1/cctvs")
    public List<CctvResponse> findAll() {
        return cctvManagementService.findAll();
    }

    @PostMapping("/api/v1/cctvs")
    @ResponseStatus(HttpStatus.CREATED)
    public CctvResponse create(@RequestBody CctvRequest request) {
        return cctvManagementService.create(request);
    }

    @PatchMapping("/api/v1/cctvs/{cctvId}")
    public CctvResponse update(@PathVariable Long cctvId, @RequestBody CctvRequest request) {
        return cctvManagementService.update(cctvId, request);
    }

    @DeleteMapping("/api/v1/cctvs/{cctvId}")
    public CctvResponse deactivate(@PathVariable Long cctvId) {
        return cctvManagementService.deactivate(cctvId);
    }
}
