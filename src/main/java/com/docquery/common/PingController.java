package com.docquery.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public ApiResponse<String> ping(@RequestParam(required = false) String param) {
        if (param != null && param.trim().isEmpty()) {
            throw new IllegalArgumentException("参数 param 不能为空 ");
        }
        if (param == null) {
            return ApiResponse.ok("ok");
        }
        return ApiResponse.ok("pong, param=" + param);
    }
}
