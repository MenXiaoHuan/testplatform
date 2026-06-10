create table test_repository (
  id bigint primary key auto_increment,
  name varchar(128) not null,
  git_url varchar(512) not null,
  default_branch varchar(128) not null,
  package_manager varchar(32) not null,
  install_command varchar(256) not null,
  run_command_template varchar(512) not null,
  test_root varchar(256) not null,
  report_relative_path varchar(256) not null,
  node_version varchar(32) not null,
  enabled tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp
);

create table scene (
  id bigint primary key auto_increment,
  repo_id bigint not null,
  name varchar(128) not null,
  branch varchar(128) not null,
  test_selector_type varchar(32) not null,
  test_selector_value varchar(512) not null,
  project_name varchar(64),
  browser varchar(64),
  env_json json,
  run_command varchar(512) not null,
  enabled tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  constraint fk_scene_repo foreign key (repo_id) references test_repository(id)
);

create table task (
  id bigint primary key auto_increment,
  scene_id bigint not null,
  repo_id bigint not null,
  status varchar(32) not null,
  trigger_type varchar(32) not null,
  trigger_user varchar(64),
  branch varchar(128) not null,
  commit_sha varchar(128),
  started_at datetime,
  finished_at datetime,
  duration_ms bigint,
  runner_name varchar(128),
  report_url varchar(1024),
  log_url varchar(1024),
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  constraint fk_task_scene foreign key (scene_id) references scene(id),
  constraint fk_task_repo foreign key (repo_id) references test_repository(id)
);

create table case_result (
  id bigint primary key auto_increment,
  task_id bigint not null,
  history_id varchar(256),
  full_name varchar(512) not null,
  suite_name varchar(256),
  story_name varchar(256),
  status varchar(32) not null,
  duration_ms bigint,
  owner_name varchar(128),
  severity varchar(64),
  project_name varchar(64),
  constraint fk_case_result_task foreign key (task_id) references task(id)
);

create table artifact (
  id bigint primary key auto_increment,
  task_id bigint not null,
  case_result_id bigint,
  artifact_type varchar(32) not null,
  bucket varchar(128) not null,
  object_key varchar(512) not null,
  content_type varchar(128),
  size bigint,
  url varchar(1024),
  constraint fk_artifact_task foreign key (task_id) references task(id)
);
