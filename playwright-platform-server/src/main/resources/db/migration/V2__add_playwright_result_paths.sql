alter table test_repository
    add column results_index_relative_path varchar(256) not null
        default 'test-results/.playwright-results.json' after report_relative_path,
    add column artifact_root_relative_path varchar(256) not null
        default '.playwright-artifacts' after results_index_relative_path;
