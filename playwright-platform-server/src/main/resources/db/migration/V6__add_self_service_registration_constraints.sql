alter table platform_user
    add constraint uk_platform_user_nickname unique (nickname);

alter table space
    add constraint uk_space_name unique (name);
