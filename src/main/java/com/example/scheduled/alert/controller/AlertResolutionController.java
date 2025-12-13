package com.example.scheduled.alert.controller;

import com.example.scheduled.alert.service.AlertResolutionService;
import com.example.scheduled.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 报警解除 API 控制器
 * 提供通用的报警解除接口，支持手动、自动、系统等多种解除方式
 */
@Slf4j
@RestController
@RequestMapping("/api/alert/resolution")
@RequiredArgsConstructor
public class AlertResolutionController {

    private final AlertResolutionService alertResolutionService;

    /**
     * 通用报警解除接口
     * 支持多种解除来源：手动解除、自动恢复、系统取消
     *
     * @param exceptionEventId 异常事件ID
     * @param resolutionReason 解除原因
     * @return 解除结果
     */
    @PostMapping("/resolve")
    public ApiResponse<?> resolveAlert(
            @RequestParam Long exceptionEventId,
            @RequestParam String resolutionReason) {
        try {
            log.info("接收报警解除请求: exceptionEventId={}, reason={}",
                    exceptionEventId, resolutionReason);

            boolean success = alertResolutionService.resolveAlert(
                    exceptionEventId,
                    resolutionReason
            );

            if (success) {
                return ApiResponse.success("报警已成功解除", exceptionEventId);
            } else {
                return ApiResponse.error("报警解除失败");
            }

        } catch (Exception e) {
            log.error("报警解除过程中出现异常: exceptionEventId={}", exceptionEventId, e);
            return ApiResponse.error("报警解除异常: " + e.getMessage());
        }
    }

    /**
     * 手动解除报警
     * 用户点击解除按钮时调用此接口
     *
     * @param exceptionEventId 异常事件ID
     * @param reason 解除原因
     * @return 解除结果
     */
    @PostMapping("/manual-resolve")
    public ApiResponse<?> manualResolveAlert(
            @RequestParam Long exceptionEventId,
            @RequestParam String reason) {
        try {
            log.info("执行手动报警解除: exceptionEventId={}, reason={}", exceptionEventId, reason);

            boolean success = alertResolutionService.manualResolveAlert(exceptionEventId, reason);

            if (success) {
                return ApiResponse.success("报警已手动解除", exceptionEventId);
            } else {
                return ApiResponse.error("手动解除失败");
            }

        } catch (Exception e) {
            log.error("手动解除报警异常", e);
            return ApiResponse.error("手动解除异常: " + e.getMessage());
        }
    }

    /**
     * 自动恢复报警
     * 业务系统检测到异常恢复，自动调用此接口
     *
     * @param exceptionEventId 异常事件ID
     * @param recoveryReason 恢复原因
     * @return 解除结果
     */
    @PostMapping("/auto-recovery")
    public ApiResponse<?> autoRecoverAlert(
            @RequestParam Long exceptionEventId,
            @RequestParam String recoveryReason) {
        try {
            log.info("执行自动报警恢复: exceptionEventId={}, reason={}", exceptionEventId, recoveryReason);

            boolean success = alertResolutionService.autoRecoverAlert(exceptionEventId, recoveryReason);

            if (success) {
                return ApiResponse.success("报警已自动恢复", exceptionEventId);
            } else {
                return ApiResponse.error("自动恢复失败");
            }

        } catch (Exception e) {
            log.error("自动恢复报警异常", e);
            return ApiResponse.error("自动恢复异常: " + e.getMessage());
        }
    }

    /**
     * 系统取消报警
     * 管理员通过管理界面取消报警
     *
     * @param exceptionEventId 异常事件ID
     * @param cancellationReason 取消原因
     * @return 取消结果
     */
    @PostMapping("/system-cancel")
    public ApiResponse<?> systemCancelAlert(
            @RequestParam Long exceptionEventId,
            @RequestParam String cancellationReason) {
        try {
            log.info("执行系统取消报警: exceptionEventId={}, reason={}", exceptionEventId, cancellationReason);

            boolean success = alertResolutionService.systemCancelAlert(exceptionEventId, cancellationReason);

            if (success) {
                return ApiResponse.success("报警已系统取消", exceptionEventId);
            } else {
                return ApiResponse.error("系统取消失败");
            }

        } catch (Exception e) {
            log.error("系统取消报警异常", e);
            return ApiResponse.error("系统取消异常: " + e.getMessage());
        }
    }
}
