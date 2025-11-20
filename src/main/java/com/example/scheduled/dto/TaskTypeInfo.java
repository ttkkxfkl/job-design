package com.example.scheduled.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskTypeInfo {
    private String code;       // 枚举名，如 LOG/EMAIL/WEBHOOK
    private String name;       // 展示名（本地化），如 “日志”
    private String description; // 可选描述
}
