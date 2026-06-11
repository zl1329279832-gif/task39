package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillTask;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DrillTaskMapper {

    @Insert("INSERT INTO drill_task(title, description, difficulty, creator_id, status) " +
            "VALUES(#{title}, #{description}, #{difficulty}, #{creatorId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DrillTask task);

    @Select("SELECT * FROM drill_task WHERE id = #{id}")
    DrillTask findById(@Param("id") Integer id);

    @Select("SELECT * FROM drill_task WHERE status = 'active' ORDER BY create_time DESC")
    List<DrillTask> findAllActive();

    @Select("SELECT * FROM drill_task WHERE creator_id = #{creatorId} ORDER BY create_time DESC")
    List<DrillTask> findByCreator(@Param("creatorId") Integer creatorId);

    @Select("SELECT * FROM drill_task ORDER BY create_time DESC")
    List<DrillTask> findAll();

    @Update("UPDATE drill_task SET title=#{title}, description=#{description}, " +
            "difficulty=#{difficulty}, status=#{status} WHERE id=#{id}")
    int update(DrillTask task);

    @Update("UPDATE drill_task SET status='archived' WHERE id=#{id}")
    int archive(@Param("id") Integer id);

    @Delete("DELETE FROM drill_task WHERE id=#{id}")
    int deleteById(@Param("id") Integer id);
}
