package com.burinake.mapper;

import com.burinake.domain.EmergencyReportRow;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmergencyReportMapper {

    @Select("SELECT nextval('emergency_report_report_id_seq')")
    Long nextId();

    @Select("""
            SELECT report_id, issue_id, vlm_result_id, report_status, report_message,
                   receiver, approved_by, approved_at, sent_at, response_code,
                   response_body, created_at, updated_at
            FROM emergency_report
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<EmergencyReportRow> findRecent(@Param("limit") int limit);

    @Select("""
            SELECT report_id, issue_id, vlm_result_id, report_status, report_message,
                   receiver, approved_by, approved_at, sent_at, response_code,
                   response_body, created_at, updated_at
            FROM emergency_report
            WHERE report_id = #{reportId}
            """)
    EmergencyReportRow findById(@Param("reportId") Long reportId);

    @Select("""
            SELECT report_id, issue_id, vlm_result_id, report_status, report_message,
                   receiver, approved_by, approved_at, sent_at, response_code,
                   response_body, created_at, updated_at
            FROM emergency_report
            WHERE issue_id = #{issueId}
            ORDER BY created_at DESC
            """)
    List<EmergencyReportRow> findByIssueId(@Param("issueId") Long issueId);

    @Insert("""
            INSERT INTO emergency_report (
                report_id, issue_id, vlm_result_id, report_status, report_message,
                receiver, approved_by, approved_at, sent_at, response_code,
                response_body, created_at, updated_at
            ) VALUES (
                #{reportId}, #{issueId}, #{vlmResultId}, #{reportStatus}, #{reportMessage},
                #{receiver}, #{approvedBy}, #{approvedAt}, #{sentAt}, #{responseCode},
                #{responseBody}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = false)
    int insert(EmergencyReportRow report);

    @Update("""
            UPDATE emergency_report
            SET report_status = #{reportStatus},
                report_message = COALESCE(#{reportMessage}, report_message),
                approved_by = COALESCE(#{approvedBy}, approved_by),
                approved_at = COALESCE(#{approvedAt}, approved_at),
                sent_at = COALESCE(#{sentAt}, sent_at),
                response_code = COALESCE(#{responseCode}, response_code),
                response_body = COALESCE(#{responseBody}, response_body),
                updated_at = #{updatedAt}
            WHERE report_id = #{reportId}
            """)
    int updateStatus(
            @Param("reportId") Long reportId,
            @Param("reportStatus") String reportStatus,
            @Param("reportMessage") String reportMessage,
            @Param("approvedBy") String approvedBy,
            @Param("approvedAt") OffsetDateTime approvedAt,
            @Param("sentAt") OffsetDateTime sentAt,
            @Param("responseCode") String responseCode,
            @Param("responseBody") String responseBody,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Delete("""
            DELETE FROM emergency_report
            WHERE report_id = #{reportId}
              AND report_status = 'DRAFT'
            """)
    int deleteDraft(@Param("reportId") Long reportId);
}
