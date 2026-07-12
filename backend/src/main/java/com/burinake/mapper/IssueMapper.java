package com.burinake.mapper;

import com.burinake.domain.IssueRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IssueMapper {

    @Select("SELECT nextval('issue_issue_id_seq')")
    Long nextId();

    @Select("SELECT issue_id FROM issue WHERE issue_id = #{issueId} FOR UPDATE")
    Long lockByIdForUpdate(@Param("issueId") Long issueId);

    @Select("""
            SELECT
                issue_id, cctv_id, trigger_image_id, yolo_result_id, issue_type, issue_status,
                detected_at, vlm_input_start_time, vlm_input_end_time, latest_is_real_fire,
                latest_level, latest_message, vlm_analyzed_at, last_detected_at,
                last_yolo_analyzed_at, last_vlm_analyzed_at, last_notified_at,
                max_box_area_ratio, last_box_area_ratio, snapshot_count, created_at, updated_at
            FROM issue
            ORDER BY COALESCE(last_detected_at, detected_at) DESC
            LIMIT #{limit}
            """)
    List<IssueRow> findRecent(@Param("limit") int limit);

    @Select("""
            SELECT
                issue_id, cctv_id, trigger_image_id, yolo_result_id, issue_type, issue_status,
                detected_at, vlm_input_start_time, vlm_input_end_time, latest_is_real_fire,
                latest_level, latest_message, vlm_analyzed_at, last_detected_at,
                last_yolo_analyzed_at, last_vlm_analyzed_at, last_notified_at,
                max_box_area_ratio, last_box_area_ratio, snapshot_count, created_at, updated_at
            FROM issue
            WHERE issue_id = #{issueId}
            """)
    IssueRow findById(@Param("issueId") Long issueId);

    @Select("""
            SELECT
                issue_id, cctv_id, trigger_image_id, yolo_result_id, issue_type, issue_status,
                detected_at, vlm_input_start_time, vlm_input_end_time, latest_is_real_fire,
                latest_level, latest_message, vlm_analyzed_at, last_detected_at,
                last_yolo_analyzed_at, last_vlm_analyzed_at, last_notified_at,
                max_box_area_ratio, last_box_area_ratio, snapshot_count, created_at, updated_at
            FROM issue
            WHERE cctv_id = #{cctvId}
              AND issue_status IN ('CANDIDATE', 'VLM_ANALYZING', 'REAL_FIRE', 'FALSE_ALARM', 'REPORTED')
              AND COALESCE(last_detected_at, detected_at) >= #{snapshotTime} - INTERVAL '5 minutes'
            ORDER BY COALESCE(last_detected_at, detected_at) DESC
            LIMIT 1
            """)
    IssueRow findOpenByCctv(
            @Param("cctvId") Long cctvId,
            @Param("snapshotTime") OffsetDateTime snapshotTime
    );

    @Insert("""
            INSERT INTO issue (
                issue_id, cctv_id, trigger_image_id, yolo_result_id, issue_type, issue_status,
                detected_at, vlm_input_start_time, vlm_input_end_time, latest_is_real_fire,
                latest_level, latest_message, vlm_analyzed_at, last_detected_at,
                last_yolo_analyzed_at, last_vlm_analyzed_at, last_notified_at,
                max_box_area_ratio, last_box_area_ratio, snapshot_count, created_at, updated_at
            ) VALUES (
                #{issueId}, #{cctvId}, #{triggerImageId}, #{yoloResultId}, #{issueType}, #{issueStatus},
                #{detectedAt}, #{vlmInputStartTime}, #{vlmInputEndTime}, #{latestIsRealFire},
                #{latestLevel}, #{latestMessage}, #{vlmAnalyzedAt}, #{lastDetectedAt},
                #{lastYoloAnalyzedAt}, #{lastVlmAnalyzedAt}, #{lastNotifiedAt},
                #{maxBoxAreaRatio}, #{lastBoxAreaRatio}, #{snapshotCount}, #{createdAt}, #{updatedAt}
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
                last_detected_at = COALESCE(#{lastDetectedAt}, last_detected_at),
                last_yolo_analyzed_at = COALESCE(#{lastYoloAnalyzedAt}, last_yolo_analyzed_at),
                last_vlm_analyzed_at = COALESCE(#{lastVlmAnalyzedAt}, last_vlm_analyzed_at),
                last_notified_at = COALESCE(#{lastNotifiedAt}, last_notified_at),
                max_box_area_ratio = COALESCE(#{maxBoxAreaRatio}, max_box_area_ratio),
                last_box_area_ratio = COALESCE(#{lastBoxAreaRatio}, last_box_area_ratio),
                updated_at = #{updatedAt}
            WHERE issue_id = #{issueId}
            """)
    int updateLatest(IssueRow issue);

    @Update("""
            UPDATE issue
            SET last_detected_at = #{lastDetectedAt},
                snapshot_count = COALESCE(snapshot_count, 0) + 1,
                updated_at = #{updatedAt}
            WHERE issue_id = #{issueId}
            """)
    int touchSnapshot(
            @Param("issueId") Long issueId,
            @Param("lastDetectedAt") OffsetDateTime lastDetectedAt,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Update("""
            UPDATE issue
            SET issue_type = #{issueType},
                issue_status = #{issueStatus},
                last_detected_at = #{lastDetectedAt},
                last_yolo_analyzed_at = #{lastYoloAnalyzedAt},
                max_box_area_ratio = GREATEST(COALESCE(max_box_area_ratio, 0), #{boxAreaRatio}),
                last_box_area_ratio = #{boxAreaRatio},
                updated_at = #{updatedAt}
            WHERE issue_id = #{issueId}
            """)
    int updateYoloTracking(
            @Param("issueId") Long issueId,
            @Param("issueType") String issueType,
            @Param("issueStatus") String issueStatus,
            @Param("lastDetectedAt") OffsetDateTime lastDetectedAt,
            @Param("lastYoloAnalyzedAt") OffsetDateTime lastYoloAnalyzedAt,
            @Param("boxAreaRatio") BigDecimal boxAreaRatio,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Update("""
            UPDATE issue
            SET issue_status = 'VLM_ANALYZING',
                last_vlm_analyzed_at = #{snapshotTime},
                updated_at = #{updatedAt}
            WHERE issue_id = #{issueId}
            """)
    int markVlmAnalysisStarted(
            @Param("issueId") Long issueId,
            @Param("snapshotTime") OffsetDateTime snapshotTime,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Update("""
            UPDATE issue
            SET issue_status = #{issueStatus},
                latest_is_real_fire = COALESCE(#{latestIsRealFire}, latest_is_real_fire),
                latest_level = COALESCE(#{latestLevel}, latest_level),
                latest_message = COALESCE(#{latestMessage}, latest_message),
                last_notified_at = COALESCE(#{lastNotifiedAt}, last_notified_at),
                updated_at = #{updatedAt}
            WHERE issue_id = #{issueId}
            """)
    int updateManualStatus(
            @Param("issueId") Long issueId,
            @Param("issueStatus") String issueStatus,
            @Param("latestIsRealFire") Boolean latestIsRealFire,
            @Param("latestLevel") Integer latestLevel,
            @Param("latestMessage") String latestMessage,
            @Param("lastNotifiedAt") OffsetDateTime lastNotifiedAt,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
