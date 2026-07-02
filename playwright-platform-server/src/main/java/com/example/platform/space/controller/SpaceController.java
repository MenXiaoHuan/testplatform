package com.example.platform.space.controller;

import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.space.dto.CreateSpaceRequest;
import com.example.platform.space.dto.SpaceSummaryResponse;
import com.example.platform.space.service.SpaceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spaces")
public class SpaceController {
    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @GetMapping
    public ApiResponse<List<SpaceSummaryResponse>> listMySpaces() {
        return ApiResponse.ok(spaceService.listMySpaces(AuthContextHolder.require()));
    }

    @PostMapping
    public ApiResponse<SpaceSummaryResponse> create(@RequestBody CreateSpaceRequest request) {
        return ApiResponse.ok(spaceService.createSpace(AuthContextHolder.require(), request));
    }
}
