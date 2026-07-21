package com.ticket.web.controller;

import com.ticket.common.dto.RouteResult;
import com.ticket.web.dto.ApiResponse;
import com.ticket.web.service.TicketService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Resource
    private TicketService ticketService;

    /** 当前生效的向量化器描述（证明“可插拔向量化”可见可控） */
    @Resource
    private String aiVectorizerDesc;

    /** 仅做语义分派（不建单），用于演示/联调 AI 路由效果 */
    @PostMapping("/route")
    public ApiResponse<RouteResult> route(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "");
        String description = body.getOrDefault("description", "");
        return ApiResponse.ok(ticketService.routeOnly(title, description));
    }

    /** 当前向量化器信息：名称 + 模式（tf-idf 本地 / dense-embedding 远程语义） */
    @GetMapping("/vectorizer")
    public ApiResponse<Map<String, Object>> vectorizer() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vectorizer", aiVectorizerDesc);
        m.put("mode", aiVectorizerDesc.startsWith("remote") ? "dense-embedding" : "tf-idf");
        return ApiResponse.ok(m);
    }

    /**
     * 主动学习·不确定样本池：返回当前低置信、待人工复核的工单
     *（模型最不确定、最值得人工标注的样本）。
     */
    @GetMapping("/uncertainty-pool")
    public ApiResponse<List<Map<String, Object>>> uncertaintyPool() {
        return ApiResponse.ok(ticketService.uncertaintyPool());
    }

    /**
     * 主动学习·标注回流：把人工标注（{"text","category"}）批量写回向量库，
     * 使模型越用越准。返回本次成功写入的条数。
     */
    @PostMapping("/label")
    public ApiResponse<Map<String, Object>> label(@RequestBody List<Map<String, String>> items) {
        int n = ticketService.labelFeedback(items);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("learned", n);
        return ApiResponse.ok(r);
    }
}
