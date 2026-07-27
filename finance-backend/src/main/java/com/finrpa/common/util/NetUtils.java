package com.finrpa.common.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;

/**
 * 网络工具类
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class NetUtils {

    /**
     * 获取客户端 IP 地址
     *
     * @param request HTTP 请求对象
     * @return 客户端 IP 地址
     */
    public static String getIpAddress(HttpServletRequest request) {
        // 1. 优先从 x-forwarded-for 头获取
        String ip = request.getHeader("x-forwarded-for");
        // 2. 依次回退到代理头
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        // 3. 仍取不到则使用远端地址，本机回环时尝试解析本机 IP
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
            if (ip.equals("127.0.0.1")) {
                try {
                    InetAddress inet = InetAddress.getLocalHost();
                    if (inet != null) {
                        ip = inet.getHostAddress();
                    }
                } catch (Exception e) {
                    // 忽略本机 IP 解析失败
                }
            }
        }
        // 4. 多个 IP 时取首个，并处理超长字符串
        if (ip != null && ip.length() > 15) {
            if (ip.indexOf(",") > 0) {
                ip = ip.substring(0, ip.indexOf(","));
            }
        }
        // 5. 兜底返回本机地址
        if (ip == null) {
            return "127.0.0.1";
        }
        return ip;
    }
}
