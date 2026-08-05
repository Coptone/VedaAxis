package dev.vedaaxis.api.execution;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FightExecutionMapper {
    @Insert("""
            INSERT INTO fight_execution(
                id, user_id, plan_id, plan_version, result, payload_json,
                started_at, ended_at, uploaded_at)
            VALUES(#{id}, #{userId}, #{planId}, #{planVersion}, #{result}, #{payloadJson},
                   #{startedAt}, #{endedAt}, #{uploadedAt})
            """)
    void insert(FightExecutionRow row);

    @Select("""
            SELECT id, user_id, plan_id, plan_version, result, payload_json,
                   started_at, ended_at, uploaded_at
            FROM fight_execution
            WHERE user_id = #{userId} AND id = #{id}
            """)
    Optional<FightExecutionRow> find(
            @Param("userId") String userId,
            @Param("id") String id);

    @Select("""
            SELECT id, user_id, plan_id, plan_version, result, payload_json,
                   started_at, ended_at, uploaded_at
            FROM fight_execution
            WHERE user_id = #{userId}
            ORDER BY started_at DESC
            LIMIT #{limit}
            """)
    List<FightExecutionRow> listRecent(
            @Param("userId") String userId,
            @Param("limit") int limit);
}
