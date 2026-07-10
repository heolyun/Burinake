package com.burinake.mapper;

import com.burinake.domain.DetectionBoxRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DetectionBoxMapper {

    @Select("SELECT nextval('detection_box_box_id_seq')")
    Long nextId();

    @Select("""
            SELECT box_id, yolo_result_id, box_order, detection_type, confidence,
                   x1, y1, x2, y2, x3, y3, x4, y4, coordinate_type, created_at
            FROM detection_box
            WHERE yolo_result_id = #{yoloResultId}
            ORDER BY box_order
            """)
    List<DetectionBoxRow> findByYoloResultId(@Param("yoloResultId") Long yoloResultId);

    @Insert("""
            INSERT INTO detection_box (
                box_id, yolo_result_id, box_order, detection_type, confidence,
                x1, y1, x2, y2, x3, y3, x4, y4, coordinate_type, created_at
            ) VALUES (
                #{boxId}, #{yoloResultId}, #{boxOrder}, #{detectionType}, #{confidence},
                #{x1}, #{y1}, #{x2}, #{y2}, #{x3}, #{y3}, #{x4}, #{y4}, #{coordinateType}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = false)
    int insert(DetectionBoxRow box);
}
