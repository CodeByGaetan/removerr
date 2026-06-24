package com.removerr.plexuser;

import jakarta.persistence.*;

@Entity
@Table(name = "plex_user")
public class PlexUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Global Plex.tv account ID — set at login, used for auth lookup. */
    @Column(name = "plex_tv_id", unique = true)
    private Long plexTvId;

    /** Local Plex server account ID — set at sync, used for viewer history matching. */
    @Column(name = "plex_account_id", unique = true)
    private Integer plexAccountId;

    @Column(nullable = false)
    private String name;

    @Column
    private String email;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(nullable = false)
    private boolean counted = true;

    @Column(name = "plex_token_encrypted", columnDefinition = "TEXT")
    private String plexTokenEncrypted;

    @Column(name = "created_at", length = 30)
    private String createdAt;

    @Column(name = "last_login_at", length = 30)
    private String lastLoginAt;

    /** Plex.tv avatar URL — secondary signal to match the admin with its local server account. */
    @Column(name = "plex_thumb", length = 512)
    private String plexThumb;

    protected PlexUser() {}

    /** Constructor for regular Plex users synced from the server (local ID known). */
    public PlexUser(int plexAccountId, String name) {
        this.plexAccountId = plexAccountId;
        this.name = name;
        this.counted = true;
        this.admin = false;
    }

    /** Constructor for the admin user created at first login (global Plex.tv ID known). */
    public PlexUser(long plexTvId, String name, String email, String plexThumb,
                    String plexTokenEncrypted, String createdAt) {
        this.plexTvId = plexTvId;
        this.name = name;
        this.email = email;
        this.plexThumb = plexThumb;
        this.plexTokenEncrypted = plexTokenEncrypted;
        this.createdAt = createdAt;
        this.counted = true;
        this.admin = true;
    }

    public Long getId() { return id; }
    public Long getPlexTvId() { return plexTvId; }
    public Integer getPlexAccountId() { return plexAccountId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public boolean isAdmin() { return admin; }
    public boolean isCounted() { return counted; }
    public String getPlexTokenEncrypted() { return plexTokenEncrypted; }
    public String getCreatedAt() { return createdAt; }
    public String getLastLoginAt() { return lastLoginAt; }
    public String getPlexThumb() { return plexThumb; }

    public void setPlexAccountId(int plexAccountId) { this.plexAccountId = plexAccountId; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public void setCounted(boolean counted) { this.counted = counted; }
    public void setPlexTokenEncrypted(String plexTokenEncrypted) { this.plexTokenEncrypted = plexTokenEncrypted; }
    public void setLastLoginAt(String lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public void setPlexThumb(String plexThumb) { this.plexThumb = plexThumb; }
}
