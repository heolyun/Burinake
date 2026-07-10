package com.burinake.mapper;

import com.burinake.domain.YoloResultRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface YoloResultMapper {

    @Select("SELECT nextval('yolo_result_yolo_result_id_seq')")
    Long nextId();

    @Select("""
            SELECT yolo_result_id, image_id, model_name, model_version, analysis_round,
                   is_fire, is_smoke, fire_confidence, smoke_confidence, raw_response, analyzed_at
            FROM yolo_result
            WHERE yolo_result_id = #{yoloResultId}
            """)
    YoloResultRow findById(@Param("yoloResultId") Long yoloResultId);

    @Insert("""
            INSERT INTO yolo_result (
                yolo_result_id, image_id, model_name, model_version, analysis_round,
                is_fire, is_smoke, fire_confidence, smoke_confidence, raw_response, analyzed_at
            ) VALUES (
                #{yoloResultId}, #{imageId}, #{modelName}, #{modelVersion}, #{analysisRound},
                #{isFire}, #{isSmoke}, #{fireConfidence}, #{smokeConfidence},
                CAST(#{rawResponse} AS jsonb), #{analyzedAt}
            )
            """)
    @Options(useGeneratedKeys = false)
    int insert(YoloResultRow result);
}
