package com.removerr.integration.plex.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlexHistoryEntry(
        @JsonProperty("ratingKey")      String ratingKey,
        @JsonProperty("key")            String key,
        @JsonProperty("type")           String type,
        @JsonProperty("accountID")      int accountId,
        @JsonProperty("index")          Integer index,
        @JsonProperty("parentIndex")    Integer parentIndex,
        @JsonProperty("grandparentKey") String grandparentKey
) {
    /** Extracts the numeric ratingKey from a path like /library/metadata/1234 */
    public String grandparentRatingKey() {
        if (grandparentKey == null) return null;
        int slash = grandparentKey.lastIndexOf('/');
        return slash >= 0 ? grandparentKey.substring(slash + 1) : grandparentKey;
    }

    /** Effective ratingKey: use direct field if present, else extract from key path */
    public String effectiveRatingKey() {
        if (ratingKey != null) return ratingKey;
        if (key == null) return null;
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(slash + 1) : key;
    }
}
