package com.example.platform.evaluation.dataset;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface EvalDatasetSampleMapper {

    @Insert("""
            insert into eval_dataset_sample (dataset_id, sample_index, input_text, golden_answer, golden_answer_versions, keywords, reference_context, metadata, scenario_tags)
            values (#{datasetId}, #{sampleIndex}, #{inputText}, #{goldenAnswer}, #{goldenAnswerVersions}, #{keywords}, #{referenceContext}, #{metadata}, #{scenarioTags})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalDatasetSampleEntity entity);

    @Insert("""
            <script>
            insert into eval_dataset_sample (dataset_id, sample_index, input_text, golden_answer, golden_answer_versions, keywords, reference_context, metadata, scenario_tags)
            values
            <foreach collection="samples" item="s" separator=",">
                (#{datasetId}, #{s.sampleIndex}, #{s.inputText}, #{s.goldenAnswer}, #{s.goldenAnswerVersions}, #{s.keywords}, #{s.referenceContext}, #{s.metadata}, #{s.scenarioTags})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("datasetId") Long datasetId, @Param("samples") List<EvalDatasetSampleEntity> samples);

    @Select("""
            select id, dataset_id, sample_index, input_text, golden_answer, golden_answer_versions, keywords,
                   reference_context, metadata, scenario_tags, created_at, updated_at
            from eval_dataset_sample
            where dataset_id = #{datasetId}
            order by sample_index asc, id asc
            """)
    @Results(id = "DatasetSampleResultMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "datasetId", column = "dataset_id"),
        @Result(property = "sampleIndex", column = "sample_index"),
        @Result(property = "inputText", column = "input_text"),
        @Result(property = "goldenAnswer", column = "golden_answer"),
        @Result(property = "goldenAnswerVersions", column = "golden_answer_versions"),
        @Result(property = "keywords", column = "keywords"),
        @Result(property = "referenceContext", column = "reference_context"),
        @Result(property = "metadata", column = "metadata"),
        @Result(property = "scenarioTags", column = "scenario_tags"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    List<EvalDatasetSampleEntity> listByDataset(Long datasetId);

    @Select("select count(*) from eval_dataset_sample where dataset_id = #{datasetId}")
    int countByDataset(Long datasetId);

    @Select("""
            select id, dataset_id, sample_index, input_text, golden_answer, golden_answer_versions, keywords,
                   reference_context, metadata, scenario_tags, created_at, updated_at
            from eval_dataset_sample
            where id = #{id}
            """)
    @ResultMap("DatasetSampleResultMap")
    Optional<EvalDatasetSampleEntity> findById(Long id);

    @Update("""
            update eval_dataset_sample
            set input_text = #{inputText},
                golden_answer = #{goldenAnswer},
                keywords = #{keywords},
                reference_context = #{referenceContext},
                metadata = #{metadata},
                scenario_tags = #{scenarioTags}
            where id = #{id}
            """)
    int update(EvalDatasetSampleEntity entity);

    @Delete("delete from eval_dataset_sample where id = #{id}")
    int deleteById(Long id);

    @Select("""
            select id, dataset_id, sample_index, input_text, golden_answer, golden_answer_versions, keywords,
                   reference_context, metadata, scenario_tags, created_at, updated_at
            from eval_dataset_sample
            where dataset_id = #{datasetId}
            order by sample_index asc, id asc
            limit #{limit} offset #{offset}
            """)
    @ResultMap("DatasetSampleResultMap")
    List<EvalDatasetSampleEntity> listByDatasetPaged(@Param("datasetId") Long datasetId,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);

    /** 按指定用例 ID 读取样本，用于离线评测只执行用户选中的用例。 */
    @Select("""
            <script>
            select id, dataset_id, sample_index, input_text, golden_answer, golden_answer_versions, keywords,
                   reference_context, metadata, scenario_tags, created_at, updated_at
            from eval_dataset_sample
            where dataset_id = #{datasetId}
              and id in
              <foreach collection="sampleIds" item="sampleId" open="(" separator="," close=")">
                  #{sampleId}
              </foreach>
            order by sample_index asc, id asc
            </script>
            """)
    @ResultMap("DatasetSampleResultMap")
    List<EvalDatasetSampleEntity> listByDatasetAndIds(@Param("datasetId") Long datasetId,
                                                      @Param("sampleIds") List<Long> sampleIds);

    @Delete("delete from eval_dataset_sample where dataset_id = #{datasetId}")
    int deleteByDataset(Long datasetId);

    /** 删除平台内置样本，保留用户自行导入的样本。 */
    @Delete("""
            delete from eval_dataset_sample
            where dataset_id = #{datasetId}
              and json_extract(metadata, '$.builtin') = true
            """)
    int deleteBuiltinSamples(Long datasetId);

    @Select("select coalesce(max(sample_index), -1) from eval_dataset_sample where dataset_id = #{datasetId}")
    int maxSampleIndex(Long datasetId);
}
