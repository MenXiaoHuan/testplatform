package com.example.platform.scene.controller;

import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.service.SceneService;
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
@RequestMapping("/api/scenes")
public class SceneController {
    private final SceneService sceneService;

    public SceneController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @PostMapping
    public SceneEntity create(@RequestBody SceneEntity entity) {
        return sceneService.create(entity);
    }

    @GetMapping
    public List<SceneEntity> list() {
        return sceneService.list();
    }

    @GetMapping("/{id}")
    public SceneEntity get(@PathVariable Long id) {
        return sceneService.get(id);
    }

    @PutMapping("/{id}")
    public SceneEntity update(@PathVariable Long id, @RequestBody SceneEntity entity) {
        return sceneService.update(id, entity);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        sceneService.delete(id);
    }
}
