package com.burinake.mapper;

import com.burinake.domain.IssueRow;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IssueMapper {

    @Select("SELECT nextval('issue_issue_id_seq')")
    Long nextId();

    @Insert("""
            INSERT INTO issue (
                issue_id, cctv_id, trigger_image_id, yolo_result_id, issue_type, issue_status,
                detected_at, vlm_input_start_time, vlm_input_end_time, latest_is_real_fire,
                latest_level, latest_message, vlm_analyzed_at, created_at, updated_at
            ) VALUES (
                #{issueId}, #{cctvId}, #{triggerImageId}, #{yoloResultId}, #{issueType}, #{issueStatus},
                #{detectedAt}, #{vlmInputStartTime}, #{vlmInputEndTime}, #{latestIsRealFire},
                #{latestLevel}, #{latestMessage}, #{vlmAnalyzedAt}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = false)
    int insert(IssueRow issue);

    @Update("""
            UPDATE issue
            SET issue_status = #{issueStatus},
                latest_is_real_fire = #{latestIsRealFire},
                latest_level = #{latestLevel},
                latest_message = #{latestMessage},
                vlm_analyzed_at = #{vlmAnalyzedAt},
                updated_at = #{updatedAt}
            WHERE issue_id = #{issueId}
            """)
    int updateLatest(IssueRow issue);
}
