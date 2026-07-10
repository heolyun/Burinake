package com.burinake.mapper;

import com.burinake.domain.CctvRow;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CctvMapper {

    @Select("SELECT nextval('cctv_cctv_id_seq')")
    Long nextId();

    @Select("""
            SELECT cctv_id, cctv_name, cctv_num, location, is_active, created_at, updated_at
            FROM cctv
            WHERE cctv_id = #{cctvId}
            """)
    CctvRow findById(@Param("cctvId") Long cctvId);

    @Select("""
            SELECT cctv_id, cctv_name, cctv_num, location, is_active, created_at, updated_at
            FROM cctv
            ORDER BY is_active DESC, cctv_id
            """)
    List<CctvRow> findAll();

    @Select("""
            SELECT cctv_id, cctv_name, cctv_num, location, is_active, created_at, updated_at
            FROM cctv
            WHERE cctv_name = #{cctvName}
              AND cctv_num = #{cctvNum}
            """)
    CctvRow findByNameAndNum(@Param("cctvName") String cctvName, @Param("cctvNum") String cctvNum);

    @Insert("""
            INSERT INTO cctv (
                cctv_id, cctv_name, cctv_num, location, is_active
            ) VALUES (
                #{cctvId}, #{cctvName}, #{cctvNum}, #{location}, #{isActive}
            )
            """)
    @Options(useGeneratedKeys = false)
    int insert(CctvRow cctv);

    @org.apache.ibatis.annotations.Update("""
            UPDATE cctv
            SET cctv_name = #{cctvName},
                cctv_num = #{cctvNum},
                location = #{location},
                is_active = #{isActive},
                updated_at = #{updatedAt}
            WHERE cctv_id = #{cctvId}
            """)
    int update(
            @Param("cctvId") Long cctvId,
            @Param("cctvName") String cctvName,
            @Param("cctvNum") String cctvNum,
            @Param("location") String location,
            @Param("isActive") Boolean isActive,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @org.apache.ibatis.annotations.Update("""
            UPDATE cctv
            SET is_active = false,
                updated_at = #{updatedAt}
            WHERE cctv_id = #{cctvId}
            """)
    int deactivate(@Param("cctvId") Long cctvId, @Param("updatedAt") OffsetDateTime updatedAt);
}
