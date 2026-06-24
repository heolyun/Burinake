package com.burinake.mapper;

import com.burinake.domain.SnapshotImageRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SnapshotImageMapper {

    @Select("SELECT nextval('snapshot_image_image_id_seq')")
    Long nextId();

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
