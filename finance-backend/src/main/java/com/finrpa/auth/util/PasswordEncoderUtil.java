package com.finrpa.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码编码工具，用于生成 BCrypt 密码哈希
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class PasswordEncoderUtil {
    /**
     * 入口方法，生成指定明文（admin）的 BCrypt 密码哈希并打印到控制台
     *
     * @param args 启动参数（未使用）
     */
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode("admin"));
    }
}