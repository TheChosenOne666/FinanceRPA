/**
 * 批量数据解析工具（前端 TS 封装，复用 .mjs 真源）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import {
  parseDelimited as _parseDelimited,
  collectColumns as _collectColumns,
  isExcelFile as _isExcelFile,
  parseExcelBuffer as _parseExcelBuffer,
} from './batchParser.mjs'

export type DelimitedRow = Record<string, string>

/** 解析分隔文本为对象数组（首行作为表头） */
export const parseDelimited = _parseDelimited as (text: string) => DelimitedRow[]

/** 提取所有出现的列名（保持首次出现顺序） */
export const collectColumns = _collectColumns as (rows: DelimitedRow[]) => string[]

/** 判断文件扩展名是否为 Excel 文件 */
export const isExcelFile = _isExcelFile as (name: string) => boolean

/** 解析 Excel 文件（ArrayBuffer）→ 取首个工作表 → 对象数组 */
export const parseExcelBuffer = _parseExcelBuffer as (buffer: ArrayBuffer) => Promise<DelimitedRow[]>
