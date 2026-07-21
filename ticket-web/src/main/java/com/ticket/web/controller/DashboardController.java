package com.ticket.web.controller;

import com.ticket.web.dashboard.SlaService;
import com.ticket.web.dto.ApiResponse;
import com.ticket.web.dto.SlaNodeStat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Resource
    private SlaService slaService;

    /** SLA 监控：各环节平均/最大处理时长 + 慢节点标记 */
    @GetMapping("/sla")
    public ApiResponse<List<SlaNodeStat>> sla() {
        return ApiResponse.ok(slaService.nodeStats());
    }
}
