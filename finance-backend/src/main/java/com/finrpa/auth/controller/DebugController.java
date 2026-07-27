package com.finrpa.auth.controller;

import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 调试控制器，仅开发环境使用
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@RestController
@RequiredArgsConstructor
public class DebugController {

    /** 用户 Mapper */
    private final UserMapper userMapper;
    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 根据用户名查询用户信息（含密码哈希），仅用于调试
     *
     * @param username 用户名
     * @return 用户信息（含是否找到、用户名、密码哈希、状态）
     */
    @GetMapping("/debug/user")
    public Map<String, Object> getUser(@RequestParam String username) {
        Map<String, Object> result = new HashMap<>();
        UserEO user = userMapper.selectByUsername(username);
        result.put("userFound", user != null);
        if (user != null) {
            result.put("username", user.getUsername());
            result.put("passwordHash", user.getPassword());
            result.put("status", user.getStatus());
        }
        return result;
    }

    /**
     * 校验原始密码与哈希是否匹配，仅用于调试
     *
     * @param rawPassword 原始密码
     * @param hash        密码哈希
     * @return 校验结果（含是否匹配、原始密码、哈希）
     */
    @GetMapping("/debug/password")
    public Map<String, Object> checkPassword(@RequestParam String rawPassword, @RequestParam String hash) {
        Map<String, Object> result = new HashMap<>();
        result.put("matches", passwordEncoder.matches(rawPassword, hash));
        result.put("rawPassword", rawPassword);
        result.put("hash", hash);
        return result;
    }
}