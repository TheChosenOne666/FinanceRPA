## 核心原则

1. **先想再写**——动手前先理解需求、提出更简单的方案、不清楚就停下问，不要急于写代码
2. **简洁优先**——用最少的代码解决问题，不做没被要求的事，不为只用一次的代码做抽象
3. **精准修改**——只改必须改的地方，不顺手重构无关代码，不改动未要求的功能
4. **目标驱动**——把任务转为可验证目标，多步骤任务先列计划再执行，完成后自我验证
5. **文档沉淀**——每完成或改动一个功能（新增/修改/删除均算）、bug修复、测试，都要同步更新需求/进度文档与设计/流程文档，不留任何代码改动无文档
6. **开发流程**——开发功能需求时，先完成后端接口开发、编写并进行单元测试、再完成前端、再进行前端测试、不需要前后端联调测试，只需要告诉我如何进行前后端联调测试的步骤就可以、最后完成文档
7**严格执行**——在提出需求要完成的任务时，要做什么就严格按照要做的完成，不要自己额外添加脑补，也不要有什么就写什么，比如要写一个需求文档和市场调研，就只把需求痛点、分析、市场调研结果写进去就可以了，如果用户有提供别的比如技术栈、架构这些等等，都不要写进去，让你干嘛就完成该干的就行了

## 动手前

- 先读相关代码再改，不要凭猜测修改
- 不确定的影响范围就先搜索，不要想当然
- 有多种方案时，简述利弊让用户选，不要默默替用户做架构决策
- 遇到模糊需求先提问澄清，不要自行脑补后大动干戈

## 写代码时

- 新增公开函数/类必须有文档注释
- 不写没被要求的注释，不为显而易见的代码加注释
- 在必要的地方要打上日志，这很重要
- 错误处理要具体，不要 `except Exception: pass` 静默吞异常
- 不硬编码密钥、地址、路径，走配置或环境变量
- 不引入未声明的新依赖，需要时先告知用户
- 临时文件、调试代码用完即删，不留在代码库里
- 前端要按照UI设计图来开发
- 每完成或改动一个功能（新增/修改/删除）都要同步更新对应的需求描述、进度与流程/设计文档

## 改代码时

- 只动必须动的行，不顺手格式化无关代码
- 不重命名未要求重命名的变量/函数
- 不调整未要求调整的目录结构
- 改动如涉及破坏性变更（API 签名、数据格式、公开接口），必须明确告知
- 每完成或改动一个功能（新增/修改/删除）都要同步更新对应的需求描述、进度与流程/设计文档

## Git 操作

- **禁止** `git push --force` 到主分支
- **禁止** `git reset --hard` 远程已有提交
- **禁止** `rm -rf` 任何路径
- 提交信息尽量使用中文且遵循 Conventional Commits：`feat:` / `fix:` / `refactor:` / `docs:` / `chore:`
- 不主动 commit，除非用户明确要求

## 沟通

- 简洁直接，不说废话
- 给结论，不啰嗦过程
- 有风险/不确定时明确标注，不要假装确定
- 用中文回复

## 后端代码风格

- 注释规范 （MANDATORY）：类级 Javadoc（含 @author / @from 标签）、字段注释、方法注释（ @param / @return ）、步骤注释（ // 1. xxx ）、行内中文注释、 // region 区域划分
- 包结构 ：annotation/aop/common/config/constant/controller/exception/manager/mapper/model(dto/entity/enums/vo)/service(impl)/utils
- 命名规范 ： XxxController 、 XxxService extends IService 、 XxxServiceImpl extends ServiceImpl 、 XxxAddRequest / XxxVO 等
- 依赖注入 ：统一 @Resource （不用 @Autowired ）、 @Slf4j 、 @Data
- 统一返回与异常 ： BaseResponse<T> + ResultUtils 、 BusinessException + ErrorCode + ThrowUtils.throwIf()
- Entity/DTO/VO ： implements Serializable + serialVersionUID 、 @TableName / @TableId / @TableLogic 、VO 必须脱敏
- Service 接口与实现分离 （MANDATORY）
- Mapper ：PostgreSQL UUID 需 ::uuid 转换
- 其他 ：参数校验、分页 PageRequest 、枚举 getEnumByValue