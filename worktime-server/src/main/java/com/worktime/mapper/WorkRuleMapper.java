package com.worktime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.worktime.entity.WorkRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkRuleMapper extends BaseMapper<WorkRule> {

    @Select("SELECT * FROM work_rule WHERE user_id = #{userId} AND is_default = 1 AND status = 1 LIMIT 1")
    WorkRule selectDefaultByUserId(Long userId);
}
