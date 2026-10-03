package com.removerr.auth;

import com.removerr.auth.dto.PlexSharedServer;
import com.removerr.integration.IntegrationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlexAuthClientTest {

    private static final String SERVER = "server-a";

    @Test
    void sharedUserIdsFor_keepsActiveSharesOfThisServer() {
        List<PlexSharedServer> shares = List.of(
                new PlexSharedServer(1L, SERVER, null, null),
                new PlexSharedServer(2L, SERVER, "2026-01-01T00:00:00Z", null),
                new PlexSharedServer(3L, SERVER, null, "2026-01-01T00:00:00Z"),
                new PlexSharedServer(4L, "server-b", null, null));

        assertThat(PlexAuthClient.sharedUserIdsFor(shares, SERVER)).containsExactly(1L);
    }

    @Test
    void sharedUserIdsFor_noShareAtAllMeansNobodyHasAccess() {
        assertThat(PlexAuthClient.sharedUserIdsFor(List.of(), SERVER)).isEmpty();
    }

    @Test
    void sharedUserIdsFor_failsWhenSharesExistButNoneForThisServer() {
        List<PlexSharedServer> shares = List.of(new PlexSharedServer(1L, "old-server-id", null, null));

        assertThatThrownBy(() -> PlexAuthClient.sharedUserIdsFor(shares, SERVER))
                .isInstanceOf(IntegrationException.class);
    }
}
