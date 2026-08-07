package dev.vedaaxis.api.rule;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AbilityMapper {
    @Select("""
            SELECT action_id, name, icon_path, job_ids, cooldown_ms, max_charges, duration_ms,
                   confirmation_strategy, source, confidence, cast_category
            FROM ability_definition ORDER BY name
            """)
    List<AbilityRow> findAll();
}
