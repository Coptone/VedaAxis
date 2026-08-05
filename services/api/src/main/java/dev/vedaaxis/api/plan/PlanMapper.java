package dev.vedaaxis.api.plan;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PlanMapper {
    @Insert("""
            INSERT INTO mitigation_plan(
                id, owner_id, name, encounter_id, territory_id, strategy_tag, track_mode, draft_json,
                latest_version, created_at, updated_at)
            VALUES(#{id}, #{ownerId}, #{name}, #{encounterId}, #{territoryId}, #{strategyTag}, #{trackMode}, #{draftJson},
                   #{latestVersion}, #{createdAt}, #{updatedAt})
            """)
    void insertPlan(PlanRow plan);

    @Select("""
            SELECT id, owner_id, name, encounter_id, territory_id, strategy_tag, track_mode, draft_json,
                   latest_version, created_at, updated_at
            FROM mitigation_plan WHERE id = #{id}
            """)
    Optional<PlanRow> findPlan(@Param("id") String id);

    @Select("""
            SELECT id, owner_id, name, encounter_id, territory_id, strategy_tag, track_mode, draft_json,
                   latest_version, created_at, updated_at
            FROM mitigation_plan WHERE owner_id = #{ownerId} ORDER BY updated_at DESC
            """)
    List<PlanRow> listByOwner(@Param("ownerId") String ownerId);

    @Update("""
            UPDATE mitigation_plan
            SET name = #{name}, strategy_tag = #{strategyTag}, draft_json = #{draftJson}, updated_at = #{updatedAt}
            WHERE id = #{id} AND owner_id = #{ownerId}
            """)
    int updateDraft(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("name") String name,
            @Param("strategyTag") String strategyTag,
            @Param("draftJson") String draftJson,
            @Param("updatedAt") Instant updatedAt);

    @Update("""
            UPDATE mitigation_plan
            SET latest_version = #{latestVersion}, draft_json = #{draftJson}, updated_at = #{updatedAt}
            WHERE id = #{id} AND owner_id = #{ownerId} AND latest_version = #{expectedVersion}
            """)
    int advanceVersion(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("expectedVersion") int expectedVersion,
            @Param("latestVersion") int latestVersion,
            @Param("draftJson") String draftJson,
            @Param("updatedAt") Instant updatedAt);

    @Insert("""
            INSERT INTO plan_version(id, plan_id, version_number, status, snapshot_json, share_code, created_at)
            VALUES(#{id}, #{planId}, #{versionNumber}, #{status}, #{snapshotJson}, #{shareCode}, #{createdAt})
            """)
    void insertVersion(PlanVersionRow version);

    @Select("""
            SELECT id, plan_id, version_number, status, snapshot_json, share_code, created_at
            FROM plan_version WHERE plan_id = #{planId} ORDER BY version_number DESC
            """)
    List<PlanVersionRow> listVersions(@Param("planId") String planId);

    @Select("""
            SELECT id, plan_id, version_number, status, snapshot_json, share_code, created_at
            FROM plan_version WHERE plan_id = #{planId} AND version_number = #{versionNumber}
            """)
    Optional<PlanVersionRow> findVersion(
            @Param("planId") String planId,
            @Param("versionNumber") int versionNumber);

    @Select("""
            SELECT id, plan_id, version_number, status, snapshot_json, share_code, created_at
            FROM plan_version WHERE share_code = #{shareCode}
            """)
    Optional<PlanVersionRow> findByShareCode(@Param("shareCode") String shareCode);

    @Select("""
            SELECT v.id, v.plan_id, v.version_number, v.status, v.snapshot_json, v.share_code, v.created_at
            FROM plan_version v
            JOIN mitigation_plan p ON p.id = v.plan_id
            WHERE p.owner_id = #{ownerId}
              AND p.territory_id = #{territoryId}
              AND p.strategy_tag = #{strategyTag}
              AND p.track_mode = #{trackMode}
              AND v.status = 'ACTIVE'
            ORDER BY v.created_at DESC
            LIMIT 1
            """)
    Optional<PlanVersionRow> findLatestActiveMatchByTerritory(
            @Param("ownerId") String ownerId,
            @Param("territoryId") long territoryId,
            @Param("strategyTag") String strategyTag,
            @Param("trackMode") String trackMode);

    @Select("""
            SELECT v.id, v.plan_id, v.version_number, v.status, v.snapshot_json, v.share_code, v.created_at
            FROM plan_version v
            JOIN mitigation_plan p ON p.id = v.plan_id
            WHERE p.owner_id = #{ownerId}
              AND p.encounter_id = #{encounterId}
              AND p.strategy_tag = #{strategyTag}
              AND p.track_mode = #{trackMode}
              AND v.status = 'ACTIVE'
            ORDER BY v.created_at DESC
            LIMIT 1
            """)
    Optional<PlanVersionRow> findLatestActiveMatchByEncounter(
            @Param("ownerId") String ownerId,
            @Param("encounterId") String encounterId,
            @Param("strategyTag") String strategyTag,
            @Param("trackMode") String trackMode);

    @Update("UPDATE plan_version SET status = 'SUPERSEDED' WHERE plan_id = #{planId} AND status = 'ACTIVE'")
    int supersedeActive(@Param("planId") String planId);
}
