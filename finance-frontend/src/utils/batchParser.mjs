/**
 * 批量数据解析工具（纯 JS 真源，供 node --test 与前端 Vite 共用）
 *
 * 将 CSV / 制表符 / 逗号分隔文本、以及 Excel(.xlsx/.xls) 文件
 * 解析为 { 列名: 值 }[]，供批量任务映射使用。
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

/**
 * 判断文件扩展名是否为 Excel 文件
 * @param {string} name 文件名
 * @returns {boolean}
 */
export function isExcelFile(name) {
  return /\.(xlsx?|xls)$/i.test(name)
}

/**
 * 解析 Excel 文件（ArrayBuffer）→ 取首个工作表 → 对象数组
 * 依赖 SheetJS(xlsx)：仅浏览器/vitest 环境可用；纯 node --test 无该依赖时调用方需自行降级。
 * @param {ArrayBuffer} buffer Excel 文件二进制
 * @returns {Array<Record<string, string>>}
 */
export async function parseExcelBuffer(buffer) {
  // 动态 import 避免无 xlsx 依赖的环境下静态加载失败
  const XLSX = await import('xlsx')
  const wb = XLSX.read(new Uint8Array(buffer), { type: 'array' })
  const firstSheet = wb.SheetNames[0]
  if (!firstSheet) return []
  const sheet = wb.Sheets[firstSheet]
  return XLSX.utils.sheet_to_json(sheet, { defval: '', raw: false })
}

/** 解析分隔文本为对象数组（首行作为表头） */
export function parseDelimited(text) {
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0)
  if (lines.length < 2) return []
  const delimiter = lines[0].includes('\t') ? '\t' : ','
  const headers = lines[0].split(delimiter).map((h) => h.trim())
  return lines.slice(1).map((line) => {
    const cells = line.split(delimiter).map((c) => c.trim())
    const row = {}
    headers.forEach((h, i) => {
      row[h] = cells[i] ?? ''
    })
    return row
  })
}

/** 提取所有出现的列名（保持首次出现顺序） */
export function collectColumns(rows) {
  const cols = []
  rows.forEach((row) => {
    Object.keys(row).forEach((k) => {
      if (!cols.includes(k)) cols.push(k)
    })
  })
  return cols
}
