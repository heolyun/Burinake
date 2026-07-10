package com.burinake.mapper;

import com.burinake.domain.SnapshotImageRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SnapshotImageMapper {

    @Select("SELECT nextval('snapshot_image_image_id_seq')")
    Long nextId();

    @Select("""
            SELECT image_id, cctv_id, storage_provider, storage_container, storage_key,
                   image_url, content_type, file_size_bytes, width_px, height_px,
                   snapshot_time, raw_metadata, created_at
            FROM snapshot_image
            WHERE image_id = #{imageId}
            """)
    SnapshotImageRow findById(@Param("imageId") Long imageId);

    @Select("""
            SELECT si.image_id, si.cctv_id, si.storage_provider, si.storage_container, si.storage_key,
                   si.image_url, si.content_type, si.file_size_bytes, si.width_px, si.height_px,
                   si.snapshot_time, si.raw_metadata, si.created_at
            FROM issue_snapshot isnap
            JOIN snapshot_image si ON si.image_id = isnap.image_id
            WHERE isnap.issue_id = #{issueId}
            ORDER BY isnap.sequence_no
            """)
    List<SnapshotImageRow> findByIssueId(@Param("issueId") Long issueId);

    @Insert("""
            INSERT INTO snapshot_image (
                image_id, cctv_id, storage_provider, storage_container, storage_key,
                image_url, content_type, file_size_bytes, width_px, height_px,
                snapshot_time, raw_metadata
            ) VALUES (
                #{imageId}, #{cctvId}, #{storageProvider}, #{storageContainer}, #{storageKey},
                #{imageUrl}, #{contentType}, #{fileSizeBytes}, #{widthPx}, #{heightPx},
                #{snapshotTime}, CAST(#{rawMetadata} AS jsonb)
            )
            """)
    @Options(useGeneratedKeys = false)
    int insert(SnapshotImageRow image);
}
