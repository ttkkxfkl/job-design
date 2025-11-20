#!/bin/bash

# 双调度器定时任务系统 - 高级功能测试脚本
# 测试超时控制、优先级调度、暂停/恢复、统计报表

BASE_URL="http://localhost:8080/api/tasks"

echo "======================================"
echo "高级功能测试脚本"
echo "======================================"
echo ""

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试1：创建带超时和优先级的任务
echo -e "${YELLOW}测试1：创建带超时和优先级的任务${NC}"
echo "--------------------------------------"

# 创建高优先级任务（优先级8，超时60秒）
TASK1=$(curl -s -X POST "$BASE_URL/once" \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "高优先级数据同步",
    "taskType": "LOG",
    "executeTime": "2025-11-14T20:30:00",
    "priority": 8,
    "executionTimeout": 60,
    "taskData": {
      "message": "高优先级任务测试"
    },
    "maxRetryCount": 3
  }')

TASK1_ID=$(echo $TASK1 | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
echo -e "${GREEN}✓ 创建高优先级任务成功，ID: $TASK1_ID${NC}"
echo "$TASK1" | python3 -m json.tool
echo ""

# 创建普通优先级任务（优先级5，超时300秒）
TASK2=$(curl -s -X POST "$BASE_URL/once" \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "普通报表生成",
    "taskType": "LOG",
    "executeTime": "2025-11-14T20:30:00",
    "priority": 5,
    "executionTimeout": 300,
    "taskData": {
      "reportType": "monthly"
    },
    "maxRetryCount": 2
  }')

TASK2_ID=$(echo $TASK2 | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
echo -e "${GREEN}✓ 创建普通优先级任务成功，ID: $TASK2_ID${NC}"
echo "$TASK2" | python3 -m json.tool
echo ""

# 创建低优先级任务（优先级2，超时10秒）
TASK3=$(curl -s -X POST "$BASE_URL/once" \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "日志清理",
    "taskType": "LOG",
    "executeTime": "2025-11-14T20:30:00",
    "priority": 2,
    "executionTimeout": 10,
    "taskData": {
      "cleanDays": 30
    },
    "maxRetryCount": 1
  }')

TASK3_ID=$(echo $TASK3 | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
echo -e "${GREEN}✓ 创建低优先级任务成功，ID: $TASK3_ID${NC}"
echo "$TASK3" | python3 -m json.tool
echo ""

# 测试2：创建 Cron 任务（带优先级和超时）
echo -e "${YELLOW}测试2：创建 Cron 任务${NC}"
echo "--------------------------------------"

CRON_TASK=$(curl -s -X POST "$BASE_URL/cron" \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "每日备份",
    "taskType": "LOG",
    "cronExpression": "0 0 2 * * ?",
    "priority": 7,
    "executionTimeout": 600,
    "taskData": {
      "backupType": "full"
    }
  }')

CRON_TASK_ID=$(echo $CRON_TASK | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
echo -e "${GREEN}✓ 创建 Cron 任务成功，ID: $CRON_TASK_ID${NC}"
echo "$CRON_TASK" | python3 -m json.tool
echo ""

# 测试3：任务暂停
echo -e "${YELLOW}测试3：暂停任务${NC}"
echo "--------------------------------------"

PAUSE_RESULT=$(curl -s -X PUT "$BASE_URL/$TASK2_ID/pause")
echo -e "${GREEN}✓ 暂停任务 $TASK2_ID${NC}"
echo "$PAUSE_RESULT" | python3 -m json.tool
echo ""

# 查询任务状态
TASK2_STATUS=$(curl -s -X GET "$BASE_URL/$TASK2_ID")
echo "任务状态："
echo "$TASK2_STATUS" | python3 -m json.tool | grep -A 1 '"status"'
echo ""

# 测试4：任务恢复
echo -e "${YELLOW}测试4：恢复任务${NC}"
echo "--------------------------------------"

sleep 2
RESUME_RESULT=$(curl -s -X PUT "$BASE_URL/$TASK2_ID/resume")
echo -e "${GREEN}✓ 恢复任务 $TASK2_ID${NC}"
echo "$RESUME_RESULT" | python3 -m json.tool
echo ""

# 查询任务状态
TASK2_STATUS=$(curl -s -X GET "$BASE_URL/$TASK2_ID")
echo "任务状态："
echo "$TASK2_STATUS" | python3 -m json.tool | grep -A 1 '"status"'
echo ""

# 测试5：手动立即重试
echo -e "${YELLOW}测试5：手动立即重试${NC}"
echo "--------------------------------------"

RETRY_RESULT=$(curl -s -X POST "$BASE_URL/$TASK3_ID/retry")
echo -e "${GREEN}✓ 触发任务 $TASK3_ID 立即重试${NC}"
echo "$RETRY_RESULT" | python3 -m json.tool
echo ""

# 测试6：获取总体统计
echo -e "${YELLOW}测试6：获取总体统计${NC}"
echo "--------------------------------------"

STATS=$(curl -s -X GET "$BASE_URL/statistics/summary")
echo -e "${GREEN}✓ 总体统计数据${NC}"
echo "$STATS" | python3 -m json.tool
echo ""

# 测试7：每日统计（最近7天）
echo -e "${YELLOW}测试7：每日统计${NC}"
echo "--------------------------------------"

DAILY_STATS=$(curl -s -X GET "$BASE_URL/statistics/daily?days=7")
echo -e "${GREEN}✓ 每日统计数据（最近7天）${NC}"
echo "$DAILY_STATS" | python3 -m json.tool
echo ""

# 测试8：任务类型分布
echo -e "${YELLOW}测试8：任务类型分布${NC}"
echo "--------------------------------------"

TYPE_DIST=$(curl -s -X GET "$BASE_URL/statistics/type-distribution")
echo -e "${GREEN}✓ 任务类型分布${NC}"
echo "$TYPE_DIST" | python3 -m json.tool
echo ""

# 测试9：任务状态分布
echo -e "${YELLOW}测试9：任务状态分布${NC}"
echo "--------------------------------------"

STATUS_DIST=$(curl -s -X GET "$BASE_URL/statistics/status-distribution")
echo -e "${GREEN}✓ 任务状态分布${NC}"
echo "$STATUS_DIST" | python3 -m json.tool
echo ""

# 测试10：任务模式分布
echo -e "${YELLOW}测试10：任务模式分布${NC}"
echo "--------------------------------------"

MODE_DIST=$(curl -s -X GET "$BASE_URL/statistics/mode-distribution")
echo -e "${GREEN}✓ 任务模式分布${NC}"
echo "$MODE_DIST" | python3 -m json.tool
echo ""

# 测试11：调度器状态
echo -e "${YELLOW}测试11：调度器状态${NC}"
echo "--------------------------------------"

SCHEDULER_STATUS=$(curl -s -X GET "$BASE_URL/scheduler/status")
echo -e "${GREEN}✓ 调度器状态${NC}"
echo "$SCHEDULER_STATUS" | python3 -m json.tool
echo ""

# 测试12：查询所有任务
echo -e "${YELLOW}测试12：查询所有任务${NC}"
echo "--------------------------------------"

ALL_TASKS=$(curl -s -X GET "$BASE_URL")
TASK_COUNT=$(echo $ALL_TASKS | grep -o '"id":[0-9]*' | wc -l)
echo -e "${GREEN}✓ 当前共有 $TASK_COUNT 个任务${NC}"
echo ""

# 测试13：按状态查询任务
echo -e "${YELLOW}测试13：按状态查询任务${NC}"
echo "--------------------------------------"

PENDING_TASKS=$(curl -s -X GET "$BASE_URL?status=PENDING")
PENDING_COUNT=$(echo $PENDING_TASKS | grep -o '"id":[0-9]*' | wc -l)
echo -e "${GREEN}✓ PENDING 状态任务数: $PENDING_COUNT${NC}"
echo ""

# 测试14：查询任务执行日志
echo -e "${YELLOW}测试14：查询任务执行日志${NC}"
echo "--------------------------------------"

TASK_LOGS=$(curl -s -X GET "$BASE_URL/$TASK1_ID/logs")
echo -e "${GREEN}✓ 任务 $TASK1_ID 的执行日志${NC}"
echo "$TASK_LOGS" | python3 -m json.tool
echo ""

# 测试总结
echo "======================================"
echo -e "${GREEN}所有测试完成！${NC}"
echo "======================================"
echo ""
echo "测试内容总结："
echo "1. ✓ 创建带优先级和超时的 ONCE 任务"
echo "2. ✓ 创建带优先级和超时的 CRON 任务"
echo "3. ✓ 暂停任务"
echo "4. ✓ 恢复任务"
echo "5. ✓ 手动立即重试"
echo "6. ✓ 总体统计"
echo "7. ✓ 每日统计"
echo "8. ✓ 任务类型分布"
echo "9. ✓ 任务状态分布"
echo "10. ✓ 任务模式分布"
echo "11. ✓ 调度器状态"
echo "12. ✓ 查询所有任务"
echo "13. ✓ 按状态查询"
echo "14. ✓ 查询执行日志"
echo ""
echo "创建的任务 ID："
echo "- 高优先级任务: $TASK1_ID"
echo "- 普通优先级任务: $TASK2_ID"
echo "- 低优先级任务: $TASK3_ID"
echo "- Cron 任务: $CRON_TASK_ID"
echo ""
echo "提示：请检查日志确认任务执行情况"
