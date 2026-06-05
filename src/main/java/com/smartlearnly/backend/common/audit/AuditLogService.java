package com.smartlearnly.backend.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    public void record(String actor, String action, String targetType, String targetId) {
        log.info("audit actor={} action={} targetType={} targetId={}", actor, action, targetType, targetId);
    }
}
