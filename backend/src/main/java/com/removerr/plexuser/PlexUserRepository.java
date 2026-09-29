package com.removerr.plexuser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlexUserRepository extends JpaRepository<PlexUser, Long> {
    Optional<PlexUser> findByPlexTvId(long plexTvId);
    Optional<PlexUser> findByPlexAccountId(int plexAccountId);
    Optional<PlexUser> findByOwnerTrueAndPlexAccountIdIsNull();
    List<PlexUser> findByServerAccessTrue();
    List<PlexUser> findByCountedTrueAndServerAccessTrueAndPlexAccountIdIsNotNull();
    long countByCountedTrueAndServerAccessTrue();
    boolean existsByOwnerTrue();
    Optional<PlexUser> findByOwnerTrue();
    List<PlexUser> findByPlexTvIdIsNull();
}
