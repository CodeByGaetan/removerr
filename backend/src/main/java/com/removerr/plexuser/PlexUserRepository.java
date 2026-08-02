package com.removerr.plexuser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlexUserRepository extends JpaRepository<PlexUser, Long> {
    Optional<PlexUser> findByPlexTvId(long plexTvId);
    Optional<PlexUser> findByPlexAccountId(int plexAccountId);
    Optional<PlexUser> findByAdminTrueAndPlexAccountIdIsNull();
    List<PlexUser> findByCountedTrueAndPlexAccountIdIsNotNull();
    long countByCounted(boolean counted);
    boolean existsByAdminTrue();
    Optional<PlexUser> findByAdminTrue();
}
