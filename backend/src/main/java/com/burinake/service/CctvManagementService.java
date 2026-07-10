package com.burinake.service;

import com.burinake.domain.CctvRow;
import com.burinake.dto.cctv.CctvRequest;
import com.burinake.dto.cctv.CctvResponse;
import com.burinake.mapper.CctvMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CctvManagementService {

    private final CctvMapper cctvMapper;

    public CctvManagementService(CctvMapper cctvMapper) {
        this.cctvMapper = cctvMapper;
    }

    public List<CctvResponse> findAll() {
        return cctvMapper.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public CctvResponse create(CctvRequest request) {
        validate(request);
        OffsetDateTime now = OffsetDateTime.now();
        CctvRow row = new CctvRow(
                cctvMapper.nextId(),
                request.cctvName().trim(),
                request.cctvNum().trim(),
                blankToNull(request.location()),
                request.isActive() == null ? true : request.isActive(),
                now,
                now
        );
        try {
            cctvMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cctvName and cctvNum already exist", ex);
        }
        return findById(row.cctvId());
    }

    public CctvResponse update(Long cctvId, CctvRequest request) {
        validate(request);
        if (cctvMapper.findById(cctvId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cctv not found");
        }
        try {
            cctvMapper.update(
                    cctvId,
                    request.cctvName().trim(),
                    request.cctvNum().trim(),
                    blankToNull(request.location()),
                    request.isActive() == null ? true : request.isActive(),
                    OffsetDateTime.now()
            );
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cctvName and cctvNum already exist", ex);
        }
        return findById(cctvId);
    }

    public CctvResponse deactivate(Long cctvId) {
        if (cctvMapper.deactivate(cctvId, OffsetDateTime.now()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cctv not found");
        }
        return findById(cctvId);
    }

    private CctvResponse findById(Long cctvId) {
        CctvRow row = cctvMapper.findById(cctvId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cctv not found");
        }
        return toResponse(row);
    }

    private CctvResponse toResponse(CctvRow row) {
        return new CctvResponse(
                row.cctvId(),
                row.cctvName(),
                row.cctvNum(),
                row.location(),
                row.isActive(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private void validate(CctvRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (request.cctvName() == null || request.cctvName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cctvName is required");
        }
        if (request.cctvNum() == null || request.cctvNum().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cctvNum is required");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
