"""审批模块（M6.2 Python LLM 风险二次判断）。

提供基于 LLM 的风险二次判断服务，接收 Java 关键词预筛结果，
通过三层容错（M5.1 ResilientCaller）调 LLM 输出最终风险等级。

@author FinanceRPA
"""
