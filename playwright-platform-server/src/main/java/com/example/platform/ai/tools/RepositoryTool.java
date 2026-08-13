package com.example.platform.ai.tools;

import com.example.platform.repository.service.RepositoryService;
import com.example.platform.repository.model.TestRepositoryEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RepositoryTool {

    private static final Logger log = LoggerFactory.getLogger(RepositoryTool.class);

    private final RepositoryService repositoryService;

    public RepositoryTool(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Tool(description = "Search for test repositories within the current space by name or keyword")
    public String searchRepository(
            @ToolParam(description = "Search keyword or repository name") String keyword,
            @ToolParam(description = "Space ID for data isolation - always required") Long spaceId) {
        log.info("AI tool: searchRepository, keyword={}, spaceId={}", keyword, spaceId);
        try {
            var repos = repositoryService.list(spaceId, 1, 20);
            List<TestRepositoryEntity> matched = repos.items().stream()
                    .filter(r -> r.getName() != null && r.getName().toLowerCase().contains(keyword.toLowerCase()))
                    .collect(Collectors.toList());
            if (matched.isEmpty()) {
                return "No repository found matching: " + keyword + " in space " + spaceId;
            }
            StringBuilder sb = new StringBuilder("Found " + matched.size() + " repositories in space " + spaceId + ":\n");
            for (TestRepositoryEntity r : matched) {
                sb.append(String.format("- [ID:%d] %s (branch: %s, url: %s, enabled: %s)%n",
                        r.getId(), r.getName(), r.getDefaultBranch(), r.getGitUrl(), r.getEnabled()));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("searchRepository failed", e);
            return "Error searching repositories: " + e.getMessage();
        }
    }

    @Tool(description = "Get repository details by ID within the current space")
    public String getRepository(
            @ToolParam(description = "Repository ID") Long repositoryId,
            @ToolParam(description = "Space ID for data isolation - always required") Long spaceId) {
        log.info("AI tool: getRepository, id={}, spaceId={}", repositoryId, spaceId);
        try {
            TestRepositoryEntity repo = repositoryService.get(spaceId, repositoryId);
            if (repo == null) {
                return "Repository not found: " + repositoryId + " in space " + spaceId;
            }
            return String.format("Repository [ID:%d]: name=%s, branch=%s, url=%s, testRoot=%s, runCmd=%s, enabled=%s",
                    repo.getId(), repo.getName(), repo.getDefaultBranch(),
                    repo.getGitUrl(), repo.getTestRoot(), repo.getRunCommandTemplate(), repo.getEnabled());
        } catch (Exception e) {
            log.error("getRepository failed", e);
            return "Error getting repository: " + e.getMessage();
        }
    }
}
