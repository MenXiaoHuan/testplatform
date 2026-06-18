package com.example.platform.repository.mapper;

import com.example.platform.repository.model.TestRepositoryEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TestRepositoryMapper {
    @Insert("""
            insert into test_repository (
                name, git_url, default_branch, working_directory, install_command,
                run_command_template, test_root, results_index_relative_path,
                artifact_root_relative_path, enabled
            ) values (
                #{name}, #{gitUrl}, #{defaultBranch}, #{workingDirectory}, #{installCommand},
                #{runCommandTemplate}, #{testRoot}, #{resultsIndexRelativePath},
                #{artifactRootRelativePath}, #{enabled}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TestRepositoryEntity entity);

    @Update("""
            update test_repository
            set name = #{name},
                git_url = #{gitUrl},
                default_branch = #{defaultBranch},
                working_directory = #{workingDirectory},
                install_command = #{installCommand},
                run_command_template = #{runCommandTemplate},
                test_root = #{testRoot},
                results_index_relative_path = #{resultsIndexRelativePath},
                artifact_root_relative_path = #{artifactRootRelativePath},
                enabled = #{enabled}
            where id = #{id}
            """)
    int update(TestRepositoryEntity entity);

    @Select("""
            select id, name, git_url, default_branch, working_directory, install_command,
                   run_command_template, test_root, results_index_relative_path,
                   artifact_root_relative_path, enabled, created_at, updated_at
            from test_repository
            where id = #{id}
            """)
    @Results(id = "TestRepositoryResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "name", column = "name"),
            @Result(property = "gitUrl", column = "git_url"),
            @Result(property = "defaultBranch", column = "default_branch"),
            @Result(property = "workingDirectory", column = "working_directory"),
            @Result(property = "installCommand", column = "install_command"),
            @Result(property = "runCommandTemplate", column = "run_command_template"),
            @Result(property = "testRoot", column = "test_root"),
            @Result(property = "resultsIndexRelativePath", column = "results_index_relative_path"),
            @Result(property = "artifactRootRelativePath", column = "artifact_root_relative_path"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<TestRepositoryEntity> findById(@Param("id") Long id);

    @Select("""
            select id, name, git_url, default_branch, working_directory, install_command,
                   run_command_template, test_root, results_index_relative_path,
                   artifact_root_relative_path, enabled, created_at, updated_at
            from test_repository
            order by updated_at desc, id desc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("TestRepositoryResultMap")
    List<TestRepositoryEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            select count(1)
            from test_repository
            """)
    long countAll();

    @Select("""
            select count(1) > 0
            from test_repository
            where lower(name) = lower(#{name})
            """)
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Select("""
            select count(1) > 0
            from test_repository
            where lower(name) = lower(#{name})
              and id <> #{id}
            """)
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    @Delete("""
            delete from test_repository
            where id = #{id}
            """)
    int deleteById(@Param("id") Long id);
}
