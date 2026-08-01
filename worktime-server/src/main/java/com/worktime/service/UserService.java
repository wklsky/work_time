package com.worktime.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worktime.common.BusinessException;
import com.worktime.common.ErrorCode;
import com.worktime.entity.*;
import com.worktime.mapper.*;
import com.worktime.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final UserSettingMapper settingMapper;
    private final WorkRuleMapper workRuleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public Map<String, Object> register(String username, String password, String phone) {
        // 检查手机号唯一
        LambdaQueryWrapper<SysUser> phoneQuery = new LambdaQueryWrapper<>();
        phoneQuery.eq(SysUser::getPhone, phone);
        if (userMapper.selectCount(phoneQuery) > 0) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }

        // 检查用户名唯一
        LambdaQueryWrapper<SysUser> nameQuery = new LambdaQueryWrapper<>();
        nameQuery.eq(SysUser::getUsername, username);
        if (userMapper.selectCount(nameQuery) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // 创建用户
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(phone);
        user.setTimezone("Asia/Shanghai");
        user.setStatus(1);
        userMapper.insert(user);

        // 初始化默认设置
        UserSetting setting = new UserSetting();
        setting.setUserId(user.getId());
        setting.setShowWorktimeAfterClockout(1);
        setting.setTimeFormat("24H");
        setting.setHomeCalendarView("TODAY");
        setting.setDefaultInputMode("BUTTON");
        settingMapper.insert(setting);

        // 初始化默认工作规则
        WorkRule rule = new WorkRule();
        rule.setUserId(user.getId());
        rule.setRuleName("默认规则");
        rule.setStartTime(LocalTime.of(9, 0));
        rule.setEndTime(LocalTime.of(18, 0));
        rule.setRestStart(LocalTime.of(12, 0));
        rule.setRestEnd(LocalTime.of(13, 0));
        rule.setBufferMinutes(30);
        rule.setDayCutTime(LocalTime.of(4, 0));
        rule.setWorkDays("1,2,3,4,5");
        rule.setIsDefault(1);
        rule.setStatus(1);
        workRuleMapper.insert(rule);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        return result;
    }

    public Map<String, Object> login(String phone, String password) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        query.eq(SysUser::getPhone, phone);
        SysUser user = userMapper.selectOne(query);

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("tokenType", "Bearer");
        result.put("expiresIn", 604800);

        Map<String, Object> userInfo = buildUserInfo(user);
        result.put("userInfo", userInfo);
        return result;
    }

    public Map<String, Object> getProfile(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        return buildUserInfo(user);
    }

    public void updateProfile(Long userId, String nickname, String avatar, String timezone) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (nickname != null) user.setNickname(nickname);
        if (avatar != null) user.setAvatar(avatar);
        if (timezone != null) user.setTimezone(timezone);
        userMapper.updateById(user);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    private Map<String, Object> buildUserInfo(SysUser user) {
        Map<String, Object> info = new HashMap<>();
        info.put("userId", user.getId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("avatar", user.getAvatar());
        info.put("phone", maskPhone(user.getPhone()));
        info.put("timezone", user.getTimezone());
        info.put("createTime", user.getCreateTime());
        return info;
    }

    private String maskPhone(String phone) {
        if (phone != null && phone.length() == 11) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        }
        return phone;
    }
}
