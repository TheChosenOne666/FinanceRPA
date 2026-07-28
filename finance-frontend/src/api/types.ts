/**
 * 后端 API 类型定义
 *
 * 对齐 finance-backend 的 DTO：
 * - BaseResponse<T> 对齐 com.finrpa.common.response.BaseResponse
 * - LoginResponse 对齐 com.finrpa.auth.dto.response.LoginResponse
 * - UserInfoResponse 对齐 com.finrpa.auth.dto.response.UserInfoResponse
 * - ErrorCode 对齐 com.finrpa.common.response.ErrorCode
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

/**
 * 后端统一响应封装
 *
 * @template T 业务数据类型
 */
export interface BaseResponse<T = unknown> {
  /** 状态码（0 表示成功，非 0 表示业务错误） */
  code: number;
  /** 响应数据 */
  data: T;
  /** 响应消息 */
  message: string;
}

/**
 * 后端业务错误码（对齐 ErrorCode 枚举）
 */
export const ErrorCode = {
  SUCCESS: 0,
  PARAMS_ERROR: 40000,
  NOT_LOGIN_ERROR: 40100,
  NO_AUTH_ERROR: 40101,
  NOT_FOUND_ERROR: 40400,
  FORBIDDEN_ERROR: 40300,
  SYSTEM_ERROR: 50000,
  OPERATION_ERROR: 50001,
} as const;

/**
 * 登录请求 DTO（对齐 com.finrpa.auth.dto.request.LoginRequest）
 */
export interface LoginRequest {
  /** 用户名（登录账号） */
  username: string;
  /** 密码 */
  password: string;
}

/**
 * 刷新 token 请求 DTO（对齐 com.finrpa.auth.dto.request.RefreshRequest）
 */
export interface RefreshRequest {
  /** 刷新令牌 */
  refreshToken: string;
}

/**
 * 权限检查请求 DTO（对齐 com.finrpa.auth.dto.request.PermissionCheckRequest）
 */
export interface PermissionCheckRequest {
  /** 资源类型 */
  resourceType: string;
  /** 资源 ID */
  resourceId: string;
  /** 操作类型 */
  action: string;
}

/**
 * 登录用户信息（LoginResponse 内嵌）
 */
export interface LoginUserInfo {
  /** 用户业务 ID */
  userId: string;
  /** 用户名 */
  username: string;
  /** 真实姓名 */
  realName: string;
  /** 所属组织 ID */
  orgId: string;
  /** 所属组织名称 */
  orgName: string;
  /** 所属部门名称 */
  deptName: string;
  /** 角色编码列表 */
  roles: string[];
}

/**
 * 登录响应 DTO（对齐 com.finrpa.auth.dto.response.LoginResponse）
 */
export interface LoginResponse {
  /** 访问令牌 */
  accessToken: string;
  /** 刷新令牌 */
  refreshToken: string;
  /** 过期时间（秒） */
  expiresIn: number;
  /** 登录用户信息 */
  user: LoginUserInfo;
}

/**
 * 用户详细信息响应 DTO（对齐 com.finrpa.auth.dto.response.UserInfoResponse）
 */
export interface UserInfoResponse {
  /** 用户业务 ID */
  userId: string;
  /** 用户名 */
  username: string;
  /** 真实姓名 */
  realName: string;
  /** 头像地址 */
  avatar?: string;
  /** 邮箱 */
  email?: string;
  /** 手机号 */
  phone?: string;
  /** 所属组织 ID */
  orgId: string;
  /** 所属组织名称 */
  orgName: string;
  /** 所属部门名称 */
  deptName: string;
  /** 角色编码列表 */
  roles: string[];
  /** 权限编码列表 */
  permissions: string[];
}

/**
 * 权限检查响应
 */
export interface PermissionCheckResponse {
  /** 是否有权限 */
  hasPermission: boolean;
}
