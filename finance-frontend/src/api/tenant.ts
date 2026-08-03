/**
 * 租户 API：部门 / 业务线列表查询
 *
 * 后端端点：
 *   GET /api/v1/tenant/departments       获取部门列表
 *   GET /api/v1/tenant/business-lines    获取业务线列表
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type { BaseResponse } from './types'

/** 部门 VO（对齐 com.finrpa.tenant.dto.response.DepartmentVO） */
export interface DepartmentVO {
  /** 部门业务 ID */
  deptId: string
  /** 部门名称 */
  deptName: string
  /** 部门编码 */
  deptCode?: string
  /** 父部门 ID（0-顶级部门） */
  parentId?: string
  /** 排序序号 */
  sortOrder?: number
}

/** 业务线 VO（对齐 com.finrpa.tenant.dto.response.BusinessLineVO） */
export interface BusinessLineVO {
  /** 业务线业务 ID */
  businessLineId: string
  /** 业务线名称 */
  businessLineName: string
  /** 业务线编码 */
  businessLineCode?: string
  /** 排序序号 */
  sortOrder?: number
}

/** 租户 API 实例 */
export const tenantApi = {
  /** 获取当前组织下的部门列表 */
  async listDepartments(): Promise<DepartmentVO[]> {
    const res = await axiosClient.get<BaseResponse<DepartmentVO[]>>('/v1/tenant/departments')
    return res.data.data
  },

  /** 获取当前组织下的业务线列表 */
  async listBusinessLines(): Promise<BusinessLineVO[]> {
    const res = await axiosClient.get<BaseResponse<BusinessLineVO[]>>('/v1/tenant/business-lines')
    return res.data.data
  },
}
