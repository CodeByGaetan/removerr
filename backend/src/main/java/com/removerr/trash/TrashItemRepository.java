package com.removerr.trash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface TrashItemRepository extends JpaRepository<TrashItem, Long> {
    List<TrashItem> findAllByOrderByTrashedAtDesc();

    @Query("SELECT t FROM TrashItem t WHERE t.purgeAt <= :now")
    List<TrashItem> findDueToPurge(@Param("now") String now);

    @Query("SELECT t.externalId FROM TrashItem t WHERE t.mediaType = :mediaType")
    Set<Integer> findTrashedExternalIds(@Param("mediaType") String mediaType);

    @Query("SELECT t FROM TrashItem t WHERE t.mediaType = 'SEASON'")
    List<TrashItem> findTrashedSeasons();
}
