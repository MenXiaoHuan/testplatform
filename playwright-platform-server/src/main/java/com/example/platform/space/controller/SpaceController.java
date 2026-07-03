package com.example.platform.space.controller;

import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.space.dto.CreateSpaceRequest;
import com.example.platform.space.dto.SpacePlazaResponse;
import com.example.platform.space.dto.SpaceSummaryResponse;
import com.example.platform.space.service.SpaceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/plaza")
    public ApiResponse<List<SpacePlazaResponse>> listSpacePlaza() {
        return ApiResponse.ok(spaceService.listSpacePlaza(AuthContextHolder.require()));
    }

    @PostMapping
    public ApiResponse<SpaceSummaryResponse> create(@RequestBody CreateSpaceRequest request) {
        return ApiResponse.ok(spaceService.createSpace(AuthContextHolder.require(), request));
    }

    @PutMapping("/{spaceId}")
    public ApiResponse<SpaceSummaryResponse> update(@PathVariable Long spaceId, @RequestBody CreateSpaceRequest request) {
        return ApiResponse.ok(spaceService.updateSpace(AuthContextHolder.require(), spaceId, request));
    }

    @DeleteMapping("/{spaceId}")
    public ApiResponse<Void> delete(@PathVariable Long spaceId) {
        spaceService.deleteSpace(AuthContextHolder.require(), spaceId);
        return ApiResponse.ok(null);
    }
}
