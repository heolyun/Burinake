package com.burinake.mapper;

import com.burinake.domain.IssueSnapshotRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IssueSnapshotMapper {

    @Select("SELECT nextval('issue_snapshot_issue_snapshot_id_seq')")
    Long nextId();

    @Insert("""
            INSERT INTO issue_snapshot (
                issue_snapshot_id, issue_id, image_id, sequence_no, relative_seconds,
                is_trigger_image, created_at
            ) VALUES (
                #{issueSnapshotId}, #{issueId}, #{imageId}, #{sequenceNo}, #{relativeSeconds},
                #{isTriggerImage}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = false)
    int insert(IssueSnapshotRow issueSnapshot);
}
