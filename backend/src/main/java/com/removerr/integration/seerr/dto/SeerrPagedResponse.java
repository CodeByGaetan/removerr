package com.removerr.integration.seerr.dto;

import java.util.List;

public record SeerrPagedResponse(PageInfo pageInfo, List<SeerrRequest> results) {
    public record PageInfo(int pages, int pageSize, int results, int page) {}
}
