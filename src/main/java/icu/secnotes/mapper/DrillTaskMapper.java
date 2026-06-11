package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillTask;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DrillTaskMapper {

    @Insert("INSERT INTO drill_task(title, description, difficulty, creator_id, status, create_time, update_time) " +
            "VALUES(#{title}, #{description}, #{difficulty}, #{creatorId}, #{status}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DrillTask task);

    @Select("SELECT * FROM drill_task WHERE id = #{id}")
    DrillTask selectById(@Param("id") Integer id);

    @Select("SELECT * FROM drill_task WHERE status = 'active' ORDER BY create_time DESC")
    List<DrillTask> selectAllActive();

    @Select("SELECT * FROM drill_task ORDER BY create_time DESC")
    List<DrillTask> selectAll();

    @Update("<script>UPDATE drill_task <set>" +
            "<if test='title != null'>title=#{title},</if>" +
            "<if test='description != null'>description=#{description},</if>" +
            "<if test='difficulty != null'>difficulty=#{difficulty},</if>" +
            "<if test='status != null'>status=#{status},</if>" +
            "update_time=#{updateTime}" +
            "</set> WHERE id=#{id}</script>")
    int update(DrillTask task);

    @Update("UPDATE drill_task SET status = 'archived', update_time = #{updateTime} WHERE id = #{id}")
    int archive(@Param("id") Integer id, @Param("updateTime") LocalDateTime updateTime);
}
