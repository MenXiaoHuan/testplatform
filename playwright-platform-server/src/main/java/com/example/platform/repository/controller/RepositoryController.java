package com.example.platform.repository.controller;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.service.RepositoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos")
public class RepositoryController {
    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping
    public ApiResponse<TestRepositoryEntity> create(@RequestBody TestRepositoryEntity entity) {
        return ApiResponse.ok(repositoryService.create(entity));
    }

    @GetMapping
    public ApiResponse<PageResponse<TestRepositoryEntity>> list(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(repositoryService.list(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<TestRepositoryEntity> get(@PathVariable Long id) {
        return ApiResponse.ok(repositoryService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TestRepositoryEntity> update(@PathVariable Long id, @RequestBody TestRepositoryEntity entity) {
        return ApiResponse.ok(repositoryService.update(id, entity));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repositoryService.delete(id);
    }
}
