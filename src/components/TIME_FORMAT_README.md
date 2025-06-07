# 时间信息说明文档

## 时间字段说明

### 出发站点时间信息
- `departureTimeBaseTimetable`: 基础时刻表计划出发时间（UTC格式）
- `departureTimePlanned`: 实际计划出发时间（UTC格式）
- `departureTimeEstimated`: 预计实际出发时间（UTC格式）

### 到达站点时间信息
- `arrivalTimeBaseTimetable`: 基础时刻表计划到达时间（UTC格式）
- `arrivalTimePlanned`: 实际计划到达时间（UTC格式）
- `arrivalTimeEstimated`: 预计实际到达时间（UTC格式）

## 时间格式说明
- 所有时间均使用 UTC 格式，例如：`2025-06-07T00:27:00Z`
- 时间会自动转换为悉尼时区显示
- 使用 dayjs 库进行时区转换

## 其他相关字段
- `duration`: 行程持续时间（单位：秒）
  - 计算方法：到达时间 - 出发时间
  - 示例：从 00:26:18 到 00:46:18 = 20分钟 = 1200秒
- `interchanges`: 换乘次数

## 示例
原始UTC时间：`2025-06-07T00:27:00Z`
悉尼当地时间：`2025-06-07 10:27:00 AEST`

## 行程时间计算示例
- 预计出发时间（UTC）：2025-06-07T00:26:18Z
- 预计到达时间（UTC）：2025-06-07T00:46:18Z
- 行程时间计算：
  1. 时间差：20分钟
  2. 转换为秒：20 * 60 = 1200秒
  3. 对应的 `duration` 值：1200 