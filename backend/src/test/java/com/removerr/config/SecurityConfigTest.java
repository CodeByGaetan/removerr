package com.removerr.config;

import com.removerr.library.MediaAggregationService;
import com.removerr.plexuser.PlexUserService;
import com.removerr.trash.PurgeService;
import com.removerr.trash.TrashService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired MockMvc mvc;

    @MockitoBean TrashService trashService;
    @MockitoBean PurgeService purgeService;
    @MockitoBean PlexUserService plexUserService;
    @MockitoBean MediaAggregationService aggregationService;

    @Test
    void anonymous_getsUnauthorizedWithErrorBody() throws Exception {
        mvc.perform(get("/api/trash"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_canBrowseTrashAndRestore() throws Exception {
        mvc.perform(get("/api/library")).andExpect(status().isOk());
        mvc.perform(get("/api/users")).andExpect(status().isOk());
        mvc.perform(get("/api/trash")).andExpect(status().isOk());
        mvc.perform(post("/api/trash/movies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"radarrId\": 1, \"immediate\": false}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/trash/1/restore")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_cannotReachAdminEndpoints() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(patch("/api/users/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"counted\": false}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/purge")).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_cannotTrashImmediately() throws Exception {
        mvc.perform(post("/api/trash/seasons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sonarrId\": 1, \"seasonNumber\": 1, \"immediate\": true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canTrashImmediatelyAndManageUsers() throws Exception {
        mvc.perform(post("/api/trash/shows").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sonarrId\": 1, \"immediate\": true}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/users/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"counted\": false}")).andExpect(status().isOk());
    }
}
