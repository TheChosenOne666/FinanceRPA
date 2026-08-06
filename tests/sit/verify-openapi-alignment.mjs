// M9.6 OpenAPI 契约校验脚本
// 拉取后端 /v3/api-docs 并与前端 types.ts 关键字段做比对
// 用法: node verify-openapi-alignment.mjs
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const openapi = JSON.parse(
  readFileSync(new URL('./openapi-docs.json', import.meta.url), 'utf8'),
)

const schemas = openapi.components.schemas
let pass = 0
let fail = 0
const failures = []

function check(name, condition, detail = '') {
  if (condition) {
    pass++
  } else {
    fail++
    failures.push(`  ✗ ${name}${detail ? ': ' + detail : ''}`)
  }
}

function schemaFields(schemaName) {
  const s = schemas[schemaName]
  if (!s) return null
  return s.properties ? Object.keys(s.properties) : null
}

function hasField(schemaName, fieldName) {
  const s = schemas[schemaName]
  if (!s || !s.properties) return false
  return Object.prototype.hasOwnProperty.call(s.properties, fieldName)
}

function fieldType(schemaName, fieldName) {
  const s = schemas[schemaName]
  if (!s || !s.properties || !s.properties[fieldName]) return null
  const p = s.properties[fieldName]
  return p.type || (p.$ref ? p.$ref.split('/').pop() : p.schema?.type || 'unknown')
}

console.log('=== M9.6 OpenAPI 契约校验 ===')
console.log(`Paths: ${Object.keys(openapi.paths).length}`)
console.log(`Schemas: ${Object.keys(schemas).length}`)
console.log('')

// ===== 1. WorkflowRunVO: 必须包含 approvalId =====
console.log('--- 1. WorkflowRunVO.approvalId ---')
check('WorkflowRunVO 存在', !!schemas.WorkflowRunVO)
check('WorkflowRunVO.approvalId 存在', hasField('WorkflowRunVO', 'approvalId'))
check('WorkflowRunVO.taskId 存在', hasField('WorkflowRunVO', 'taskId'))
check('WorkflowRunVO.workflowId 存在', hasField('WorkflowRunVO', 'workflowId'))
check('WorkflowRunVO.state 存在', hasField('WorkflowRunVO', 'state'))

// ===== 2. NeedsHumanQueueVO: 必须包含 taskTitle / subtaskGoal =====
console.log('--- 2. NeedsHumanQueueVO.taskTitle / subtaskGoal ---')
check('NeedsHumanQueueVO 存在', !!schemas.NeedsHumanQueueVO)
check('NeedsHumanQueueVO.taskTitle 存在', hasField('NeedsHumanQueueVO', 'taskTitle'))
check('NeedsHumanQueueVO.subtaskGoal 存在', hasField('NeedsHumanQueueVO', 'subtaskGoal'))
check('NeedsHumanQueueVO.businessLineName 存在', hasField('NeedsHumanQueueVO', 'businessLineName'))

// ===== 3. ApprovalQueryRequest: 必须包含 userId =====
console.log('--- 3. ApprovalQueryRequest.userId ---')
check('ApprovalQueryRequest 存在', !!schemas.ApprovalQueryRequest)
check('ApprovalQueryRequest.userId 存在', hasField('ApprovalQueryRequest', 'userId'))
check('ApprovalQueryRequest.status 存在', hasField('ApprovalQueryRequest', 'status'))
check('ApprovalQueryRequest.riskLevel 存在', hasField('ApprovalQueryRequest', 'riskLevel'))

// ===== 4. TaskQueryRequest: sortField / sortOrder =====
console.log('--- 4. TaskQueryRequest.sortField / sortOrder ---')
check('TaskQueryRequest 存在', !!schemas.TaskQueryRequest)
check('TaskQueryRequest.sortField 存在', hasField('TaskQueryRequest', 'sortField'))
check('TaskQueryRequest.sortOrder 存在', hasField('TaskQueryRequest', 'sortOrder'))
check('TaskQueryRequest.businessLineId 存在', hasField('TaskQueryRequest', 'businessLineId'))
check('TaskQueryRequest.departmentId 存在', hasField('TaskQueryRequest', 'departmentId'))

// ===== 5. Long 类型字段：OpenAPI 中应体现为 integer(int64) =====
console.log('--- 5. Long 类型字段（integer/int64）---')
check('TaskVO.taskId 为 integer', fieldType('TaskVO', 'taskId') === 'integer')
check('TaskVO.durationMs 为 integer', fieldType('TaskVO', 'durationMs') === 'integer')
check('ApprovalRequestVO.approvalId 为 integer', fieldType('ApprovalRequestVO', 'approvalId') === 'integer')
check('NeedsHumanQueueVO.queueId 为 integer', fieldType('NeedsHumanQueueVO', 'queueId') === 'integer')

// ===== 6. 关键路径存在性校验 =====
console.log('--- 6. 关键 API 路径 ---')
const expectedPaths = [
  '/tasks',
  '/tasks/{taskId}',
  '/workflows',
  '/workflows/{workflowId}',
  '/workflows/{workflowId}/run',
  '/approvals',
  '/approvals/{approvalId}',
  '/approvals/{approvalId}/approve',
  '/approvals/{approvalId}/reject',
  '/llm/needs-human',
  '/llm/needs-human/{queueId}',
  '/llm/needs-human/{queueId}/resolve',
  '/llm/calls/stats',
  '/v1/audit/logs',
  '/v1/dashboard/overview',
]
for (const p of expectedPaths) {
  check(`Path ${p} 存在`, !!openapi.paths[p])
}

// ===== 7. ApprovalRequestVO: userName 字段 =====
console.log('--- 7. ApprovalRequestVO.userName ---')
check('ApprovalRequestVO.userName 存在', hasField('ApprovalRequestVO', 'userName'))
check('ApprovalRequestVO.riskReasoning 存在', hasField('ApprovalRequestVO', 'riskReasoning'))
check('ApprovalRequestVO.requestPayload 存在', hasField('ApprovalRequestVO', 'requestPayload'))

// ===== 8. TaskVO: 扩展字段（M7.6） =====
console.log('--- 8. TaskVO 扩展字段 ---')
check('TaskVO.userName 存在', hasField('TaskVO', 'userName'))
check('TaskVO.riskLevel 存在', hasField('TaskVO', 'riskLevel'))
check('TaskVO.departmentName 存在', hasField('TaskVO', 'departmentName'))
check('TaskVO.businessLineName 存在', hasField('TaskVO', 'businessLineName'))

// ===== 9. ChannelVO: webhookUrl / enabled =====
console.log('--- 9. ChannelVO.webhookUrl / enabled ---')
check('ChannelVO.webhookUrl 存在', hasField('ChannelVO', 'webhookUrl'))
check('ChannelVO.enabled 存在', hasField('ChannelVO', 'enabled'))
check('ChannelVO.configured 存在', hasField('ChannelVO', 'configured'))

// ===== 10. 审批超时/路由配置 VO =====
console.log('--- 10. 风控配置 VO ---')
check('ApprovalTimeoutConfigVO 存在', !!schemas.ApprovalTimeoutConfigVO)
check('ApprovalRouteConfigVO 存在', !!schemas.ApprovalRouteConfigVO)
check('ApprovalRouteConfigVO.approverName 存在', hasField('ApprovalRouteConfigVO', 'approverName'))

console.log('')
console.log('=== 校验结果 ===')
console.log(`通过: ${pass}`)
console.log(`失败: ${fail}`)
if (failures.length > 0) {
  console.log('失败项:')
  failures.forEach((f) => console.log(f))
  process.exit(1)
} else {
  console.log('✓ 全部通过')
}
