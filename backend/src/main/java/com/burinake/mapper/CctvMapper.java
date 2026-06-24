package com.burinake.mapper;

import com.burinake.domain.CctvRow;
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
}
