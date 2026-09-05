package com.docquery.eval;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docquery.common.ApiResponse;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    /** replace=true 才重建同名文档，避免每次评测重复打 Embedding。 */
    @PostMapping("/seed-corpus")
    public ResponseEntity<ApiResponse<EvalService.SeedResult>> seedCorpus(
            @RequestParam(value = "replace", defaultValue = "false") boolean replace) {
        return ResponseEntity.ok(ApiResponse.ok(evalService.seedCorpus(replace)));
    }

    /** 不调生成；结果另写 eval-runs/，不进简历。 */
    @PostMapping("/goldenset")
    public ResponseEntity<ApiResponse<EvalService.EvalReport>> runGoldenset() {
        return ResponseEntity.ok(ApiResponse.ok(evalService.runGoldenset()));
    }
}
