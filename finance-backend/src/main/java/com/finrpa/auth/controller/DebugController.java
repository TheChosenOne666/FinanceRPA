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

@RestController
@RequiredArgsConstructor
public class DebugController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

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

    @GetMapping("/debug/password")
    public Map<String, Object> checkPassword(@RequestParam String rawPassword, @RequestParam String hash) {
        Map<String, Object> result = new HashMap<>();
        result.put("matches", passwordEncoder.matches(rawPassword, hash));
        result.put("rawPassword", rawPassword);
        result.put("hash", hash);
        return result;
    }
}