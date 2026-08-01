package com.worktime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.worktime.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {

    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND status = 'PENDING' ORDER BY clock_in DESC LIMIT 1")
    AttendanceRecord selectPendingByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND work_date = #{workDate} ORDER BY clock_in ASC")
    List<AttendanceRecord> selectByUserAndDate(@Param("userId") Long userId, @Param("workDate") LocalDate workDate);

    @Select("SELECT * FROM attendance_record WHERE user_id = #{userId} AND work_date >= #{startDate} AND work_date <= #{endDate} AND status = 'COMPLETED' ORDER BY work_date, clock_in")
    List<AttendanceRecord> selectByUserAndDateRange(@Param("userId") Long userId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);
}
