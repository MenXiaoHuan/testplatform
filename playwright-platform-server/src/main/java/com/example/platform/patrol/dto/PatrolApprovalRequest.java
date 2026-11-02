package com.example.platform.patrol.dto;

import java.util.List;

/**
 * 执行前一次性审批。
 *
 * <p>approvedCaseIds 为空表示按类别授权；非空表示只授权列出的用例。
 */
public record PatrolApprovalRequest(
        Boolean allowWrite,
        Boolean allowDestructive,
        List<Long> approvedCaseIds
) {
}
