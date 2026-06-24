package com.removerr.audit;

import com.removerr.plexuser.PlexUser;
import jakarta.persistence.*;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private PlexUser user;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id", length = 255)
    private String targetId;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, length = 30)
    private String createdAt;

    protected AuditLog() {}

    public AuditLog(PlexUser user, String action, String targetType, String targetId,
                    String metadataJson, String createdAt) {
        this.user = user;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public PlexUser getUser() { return user; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getMetadataJson() { return metadataJson; }
    public String getCreatedAt() { return createdAt; }
}
