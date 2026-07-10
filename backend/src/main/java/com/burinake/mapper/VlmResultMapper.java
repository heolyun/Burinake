package com.burinake.mapper;

import com.burinake.domain.VlmResultRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VlmResultMapper {

    @Select("SELECT nextval('vlm_result_vlm_result_id_seq')")
    Long nextId();

    @Select("""
            SELECT COALESCE(MAX(analysis_round), 0) + 1
            FROM vlm_result
            WHERE issue_id = #{issueId}
            """)
    Integer nextAnalysisRound(@Param("issueId") Long issueId);

    @Select("""
            SELECT vlm_result_id, issue_id, analysis_round, model_name, model_version,
                   is_real_fire, fire_start, fire_reason, situation_summary, level,
                   message, confidence, raw_response, analyzed_at
            FROM vlm_result
            WHERE issue_id = #{issueId}
            ORDER BY analysis_round DESC
            LIMIT 1
            """)
    VlmResultRow findLatestByIssueId(@Param("issueId") Long issueId);

    @Select("""
            SELECT vlm_result_id, issue_id, analysis_round, model_name, model_version,
                   is_real_fire, fire_start, fire_reason, situation_summary, level,
                   message, confidence, raw_response, analyzed_at
            FROM vlm_result
            WHERE issue_id = #{issueId}
            ORDER BY analysis_round DESC
            """)
    List<VlmResultRow> findByIssueId(@Param("issueId") Long issueId);

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
