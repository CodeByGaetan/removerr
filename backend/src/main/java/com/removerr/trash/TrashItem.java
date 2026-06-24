package com.removerr.trash;

import com.removerr.plexuser.PlexUser;
import jakarta.persistence.*;

@Entity
@Table(name = "trash_item")
public class TrashItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_type", nullable = false, length = 10)
    private String mediaType;

    @Column(name = "external_service", nullable = false, length = 10)
    private String externalService;

    @Column(name = "external_id", nullable = false)
    private int externalId;

    @Column(name = "tmdb_id")
    private Integer tmdbId;

    @Column(name = "tvdb_id")
    private Integer tvdbId;

    @Column(name = "season_number")
    private Integer seasonNumber;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "year")
    private Integer year;

    @Column(name = "poster_url", length = 1024)
    private String posterUrl;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "trashed_at", nullable = false, length = 30)
    private String trashedAt;

    @Column(name = "purge_at", nullable = false, length = 30)
    private String purgeAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trashed_by_user_id", nullable = false)
    private PlexUser trashedBy;

    @Column(name = "seerr_requested_by", length = 255)
    private String seerrRequestedBy;

    protected TrashItem() {}

    public TrashItem(String mediaType, String externalService, int externalId,
                     Integer tmdbId, Integer tvdbId, Integer seasonNumber,
                     String title, Integer year, String posterUrl, long sizeBytes,
                     String trashedAt, String purgeAt, PlexUser trashedBy,
                     String seerrRequestedBy) {
        this.mediaType = mediaType;
        this.externalService = externalService;
        this.externalId = externalId;
        this.tmdbId = tmdbId;
        this.tvdbId = tvdbId;
        this.seasonNumber = seasonNumber;
        this.title = title;
        this.year = year;
        this.posterUrl = posterUrl;
        this.sizeBytes = sizeBytes;
        this.trashedAt = trashedAt;
        this.purgeAt = purgeAt;
        this.trashedBy = trashedBy;
        this.seerrRequestedBy = seerrRequestedBy;
    }

    public Long getId() { return id; }
    public String getMediaType() { return mediaType; }
    public String getExternalService() { return externalService; }
    public int getExternalId() { return externalId; }
    public Integer getTmdbId() { return tmdbId; }
    public Integer getTvdbId() { return tvdbId; }
    public Integer getSeasonNumber() { return seasonNumber; }
    public String getTitle() { return title; }
    public Integer getYear() { return year; }
    public String getPosterUrl() { return posterUrl; }
    public long getSizeBytes() { return sizeBytes; }
    public String getTrashedAt() { return trashedAt; }
    public String getPurgeAt() { return purgeAt; }
    public PlexUser getTrashedBy() { return trashedBy; }
    public String getSeerrRequestedBy() { return seerrRequestedBy; }
}
