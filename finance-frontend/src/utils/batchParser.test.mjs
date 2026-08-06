/**
 * batchParser 纯逻辑测试（node --test 运行，无需额外依赖）
 *
 * 覆盖 CSV / 制表符解析、空内容、列名提取，对应 BatchTaskModal 的数据解析路径。
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseDelimited, collectColumns, isExcelFile, parseExcelBuffer } from './batchParser.mjs'

test('解析逗号 CSV：首行表头 + 多行数据', () => {
  const text = '客户姓名,身份证号,保额\n张三,110,50万\n李四,120,30万'
  const rows = parseDelimited(text)
  assert.equal(rows.length, 2)
  assert.equal(rows[0]['客户姓名'], '张三')
  assert.equal(rows[0]['身份证号'], '110')
  assert.equal(rows[1]['保额'], '30万')
})

test('解析制表符分隔文本', () => {
  const text = 'name\tid\n王五\t330'
  const rows = parseDelimited(text)
  assert.equal(rows.length, 1)
  assert.equal(rows[0]['name'], '王五')
  assert.equal(rows[0]['id'], '330')
})

test('空内容或仅表头返回空数组', () => {
  assert.deepEqual(parseDelimited(''), [])
  assert.deepEqual(parseDelimited('a,b,c'), [])
  assert.deepEqual(parseDelimited('   \n  \n'), [])
})

test('列数少于表头时缺省为空字符串', () => {
  const rows = parseDelimited('a,b,c\n1,2')
  assert.equal(rows[0]['c'], '')
})

test('collectColumns 保持首次出现顺序且去重', () => {
  const rows = [
    { a: '1', b: '2' },
    { b: '3', c: '4' },
  ]
  assert.deepEqual(collectColumns(rows), ['a', 'b', 'c'])
})

test('isExcelFile 正确识别扩展名', () => {
  assert.equal(isExcelFile('data.xlsx'), true)
  assert.equal(isExcelFile('data.XLS'), true)
  assert.equal(isExcelFile('data.xls'), true)
  assert.equal(isExcelFile('data.csv'), false)
  assert.equal(isExcelFile('data.tsv'), false)
})

test('parseExcelBuffer 解析 xlsx 首个工作表为对象数组', async () => {
  const XLSX = await import('xlsx')
  const data = [
    { 客户姓名: '张三', 身份证号: '110', 保额: '50万' },
    { 客户姓名: '李四', 身份证号: '120', 保额: '30万' },
  ]
  const ws = XLSX.utils.json_to_sheet(data)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
  const buf = XLSX.write(wb, { type: 'array', bookType: 'xlsx' })

  const rows = await parseExcelBuffer(buf)
  assert.equal(rows.length, 2)
  assert.equal(rows[0]['客户姓名'], '张三')
  assert.equal(rows[1]['保额'], '30万')
})

test('parseExcelBuffer 仅有表头无数据行返回空数组', async () => {
  const XLSX = await import('xlsx')
  const ws = XLSX.utils.json_to_sheet([])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
  const buf = XLSX.write(wb, { type: 'array', bookType: 'xlsx' })
  assert.deepEqual(await parseExcelBuffer(buf), [])
})
