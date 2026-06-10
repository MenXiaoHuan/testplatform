package com.example.platform.repository.controller;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.service.RepositoryService;
import java.util.List;
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
    public TestRepositoryEntity create(@RequestBody TestRepositoryEntity entity) {
        return repositoryService.create(entity);
    }

    @GetMapping
    public List<TestRepositoryEntity> list() {
        return repositoryService.list();
    }

    @GetMapping("/{id}")
    public TestRepositoryEntity get(@PathVariable Long id) {
        return repositoryService.get(id);
    }

    @PutMapping("/{id}")
    public TestRepositoryEntity update(@PathVariable Long id, @RequestBody TestRepositoryEntity entity) {
        return repositoryService.update(id, entity);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repositoryService.delete(id);
    }
}
