package com.removerr.auth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.removerr.auth.dto.PlexHomeUser;
import com.removerr.auth.dto.PlexHomeUsersResponse;
import com.removerr.auth.dto.PlexSharedServer;
import com.removerr.integration.IntegrationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlexAuthClientTest {

    private static final String SERVER = "server-a";

    @Test
    void sharedUsersFor_keepsActiveSharesOfThisServer() {
        List<PlexSharedServer> shares = List.of(
                share(1L, SERVER, null, null),
                share(2L, SERVER, "2026-01-01T00:00:00Z", null),
                share(3L, SERVER, null, "2026-01-01T00:00:00Z"),
                share(4L, "server-b", null, null));

        assertThat(PlexAuthClient.sharedUsersFor(shares, SERVER)).containsExactly(Map.entry(1L, "user-1"));
    }

    @Test
    void sharedUsersFor_noShareAtAllMeansNobodyHasAccess() {
        assertThat(PlexAuthClient.sharedUsersFor(List.of(), SERVER)).isEmpty();
    }

    @Test
    void sharedUsersFor_failsWhenSharesExistButNoneForThisServer() {
        List<PlexSharedServer> shares = List.of(share(1L, "old-server-id", null, null));

        assertThatThrownBy(() -> PlexAuthClient.sharedUsersFor(shares, SERVER))
                .isInstanceOf(IntegrationException.class);
    }

    @Test
    void homeUsersResponse_readsIdRestrictedAndGuestFromObservedFormat() throws Exception {
        // Shape returned by plex.tv/api/v2/home/users on a real account (personal fields removed).
        String json = """
                {"subscription": true, "users": [
                  {"id": 76217485, "uuid": "x", "title": "x", "username": "x", "hasPassword": true,
                   "restricted": false, "admin": false, "guest": false, "protected": true},
                  {"id": 869858736, "uuid": "y", "title": "Guest", "restricted": true, "guest": true},
                  {"id": 99, "uuid": "z", "title": "Kid", "restricted": true, "guest": false}
                ]}""";

        PlexHomeUsersResponse response = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(json, PlexHomeUsersResponse.class);

        assertThat(response.users()).containsExactly(new PlexHomeUser(76217485L, "x", false, false),
                new PlexHomeUser(869858736L, "Guest", true, true), new PlexHomeUser(99L, "Kid", true, false));
    }

    private static PlexSharedServer share(long invitedId, String machine, String deletedAt, String leftAt) {
        return new PlexSharedServer(invitedId, machine, deletedAt, leftAt,
                new PlexSharedServer.Invited("user-" + invitedId));
    }
}
