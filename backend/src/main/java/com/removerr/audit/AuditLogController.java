package com.removerr.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/api/audit")
    public List<AuditLogResponse> getAuditLog(@RequestParam(defaultValue = "100") int limit) {
        return auditLogService.getRecent(Math.min(limit, 500));
    }
}
