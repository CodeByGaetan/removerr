package com.removerr.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;
    private final PlexUserRepository plexUserRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository repository, PlexUserRepository plexUserRepository,
                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.plexUserRepository = plexUserRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String targetType, String targetId, Map<String, Object> metadata) {
        PlexUser user = (userId != null) ? plexUserRepository.getReferenceById(userId) : null;
        repository.save(new AuditLog(user, action, targetType, targetId, toJson(metadata), Instant.now().toString()));
    }

    // Insertion order is significant: the audit UI keys off the first entry being "title".
    public static Map<String, Object> meta(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}"; // never let audit serialization break the audited operation
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getRecent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                .stream().map(this::toResponse).toList();
    }

    private AuditLogResponse toResponse(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getUser() != null ? entry.getUser().getId() : null,
                entry.getUser() != null ? entry.getUser().getName() : "system",
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getMetadataJson(),
                entry.getCreatedAt()
        );
    }
}
