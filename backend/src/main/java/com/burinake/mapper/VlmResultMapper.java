package com.burinake.mapper;

import com.burinake.domain.VlmResultRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VlmResultMapper {

    @Select("SELECT nextval('vlm_result_vlm_result_id_seq')")
    Long nextId();

    @Insert("""
            INSERT INTO vlm_result (
                vlm_result_id, issue_id, analysis_round, model_name, model_version,
                is_real_fire, fire_start, fire_reason, situation_summary, level,
                message, confidence, raw_response, analyzed_at
            ) VALUES (
                #{vlmResultId}, #{issueId}, #{analysisRound}, #{modelName}, #{modelVersion},
                #{isRealFire}, #{fireStart}, #{fireReason}, #{situationSummary}, #{level},
                #{message}, #{confidence}, CAST(#{rawResponse} AS jsonb), #{analyzedAt}
            )
            """)
    @Options(useGeneratedKeys = false)
    int insert(VlmResultRow vlmResult);
}
