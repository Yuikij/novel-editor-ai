-- 用户表
CREATE TABLE `users` (
                         `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                         `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
                         `email` VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
                         `password_hash` VARCHAR(255) NOT NULL COMMENT '加密后的密码',
                         `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- 项目表
CREATE TABLE `projects` (
                            `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '项目ID',
                            `user_id` INT UNSIGNED NOT NULL COMMENT '所属用户ID',
                            `title` VARCHAR(150) NOT NULL COMMENT '项目标题',
                            `genre` VARCHAR(50) NULL COMMENT '类型/题材',
                            `style` VARCHAR(50) NULL COMMENT '风格',
                            `synopsis` TEXT NULL COMMENT '故事概要',
                            `tags` JSON NULL COMMENT '标签 (存储为JSON数组)',
                            `target_audience` VARCHAR(100) NULL COMMENT '目标读者',
                            `word_count_goal` INT UNSIGNED NULL COMMENT '字数目标',
                            `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '项目状态 (draft, in-progress, completed, published)',
                            `cover_gradient_start` VARCHAR(7) NULL COMMENT '封面渐变色开始 (hex)',
                            `cover_gradient_end` VARCHAR(7) NULL COMMENT '封面渐变色结束 (hex)',
                            `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            INDEX `idx_projects_user_id` (`user_id`),
                            FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE -- 用户删除时，其项目也删除
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说项目信息表';

-- 章节表
CREATE TABLE `chapters` (
                            `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '章节ID',
                            `project_id` INT UNSIGNED NOT NULL COMMENT '所属项目ID',
                            `title` VARCHAR(200) NOT NULL COMMENT '章节标题',
                            `content` LONGTEXT NULL COMMENT '章节内容',
                            `word_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '字数统计',
                            `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '章节状态 (draft, in-progress, completed)',
                            `summary` TEXT NULL COMMENT '章节概要',
                            `chapter_order` INT NOT NULL DEFAULT 0 COMMENT '章节顺序',
                            `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            INDEX `idx_chapters_project_id` (`project_id`),
                            FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE -- 项目删除时，其章节也删除
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说章节表';

-- 角色表
CREATE TABLE `characters` (
                              `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
                              `project_id` INT UNSIGNED NOT NULL COMMENT '所属项目ID',
                              `name` VARCHAR(100) NOT NULL COMMENT '角色名称',
                              `role` VARCHAR(50) NULL COMMENT '角色定位 (protagonist, antagonist, supporting)',
                              `personality` JSON NULL COMMENT '性格特点 (存储为JSON数组)',
                              `background` TEXT NULL COMMENT '背景故事',
                              `goals` JSON NULL COMMENT '角色目标 (存储为JSON数组)',
                              `avatar_url` VARCHAR(255) NULL COMMENT '角色头像URL',
                              `notes` TEXT NULL COMMENT '其他备注',
                              `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              INDEX `idx_characters_project_id` (`project_id`),
                              FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE -- 项目删除时，其角色也删除
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说角色表';

-- 角色关系表 (处理角色之间的关系)
CREATE TABLE `character_relationships` (
                                           `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关系ID',
                                           `project_id` INT UNSIGNED NOT NULL COMMENT '所属项目ID (冗余方便查询)',
                                           `source_character_id` INT UNSIGNED NOT NULL COMMENT '关系发起方角色ID',
                                           `target_character_id` INT UNSIGNED NOT NULL COMMENT '关系目标方角色ID',
                                           `relationship_type` VARCHAR(50) NOT NULL COMMENT '关系类型 (love, family, friend, rival, enemy)',
                                           `description` TEXT NULL COMMENT '关系描述',
                                           `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                           `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                           PRIMARY KEY (`id`),
                                           INDEX `idx_char_relationships_project` (`project_id`),
                                           INDEX `idx_char_relationships_source` (`source_character_id`),
                                           INDEX `idx_char_relationships_target` (`target_character_id`),
                                           FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE,
                                           FOREIGN KEY (`source_character_id`) REFERENCES `characters` (`id`) ON DELETE CASCADE,
                                           FOREIGN KEY (`target_character_id`) REFERENCES `characters` (`id`) ON DELETE CASCADE,
                                           UNIQUE KEY `uk_character_relationship` (`source_character_id`, `target_character_id`, `relationship_type`) -- 防止重复定义同类关系
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色关系表';


-- 世界观设定表
CREATE TABLE `worlds` (
                          `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '世界观ID',
                          `project_id` INT UNSIGNED NOT NULL COMMENT '所属项目ID',
                          `name` VARCHAR(150) NOT NULL COMMENT '世界观/设定集名称',
                          `description` TEXT NULL COMMENT '整体描述',
                          `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          INDEX `idx_worlds_project_id` (`project_id`),
                          FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE -- 项目删除时，其世界观设定也删除
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界观设定集表';

-- 世界观元素表 (具体设定条目)
CREATE TABLE `world_elements` (
                                  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '设定元素ID',
                                  `world_id` INT UNSIGNED NOT NULL COMMENT '所属世界观ID',
                                  `type` VARCHAR(50) NOT NULL COMMENT '元素类型 (location, culture, history, technology, species, magic_system)',
                                  `name` VARCHAR(150) NOT NULL COMMENT '元素名称',
                                  `description` TEXT NULL COMMENT '元素详细描述',
                                  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  INDEX `idx_world_elements_world_id` (`world_id`),
                                  FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`) ON DELETE CASCADE -- 世界观删除时，其元素也删除
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界观具体元素表';


-- 情节/大纲结构表
CREATE TABLE `plots` (
                         `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '情节结构ID',
                         `project_id` INT UNSIGNED NOT NULL COMMENT '所属项目ID',
                         `title` VARCHAR(150) NOT NULL COMMENT '情节/大纲标题 (如 主线, 支线A)',
                         `type` VARCHAR(50) NULL COMMENT '结构类型 (three-act, hero-journey, custom)',
                         `description` TEXT NULL COMMENT '整体描述',
                         `plot_order` INT NOT NULL DEFAULT 0 COMMENT '情节结构排序',
                         `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         PRIMARY KEY (`id`),
                         INDEX `idx_plots_project_id` (`project_id`),
                         FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE -- 项目删除时，其情节也删除
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情节/大纲结构表';

-- 情节元素表 (具体情节节点/场景)
CREATE TABLE `plot_elements` (
                                 `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '情节元素ID',
                                 `plot_id` INT UNSIGNED NOT NULL COMMENT '所属情节结构ID',
                                 `title` VARCHAR(200) NOT NULL COMMENT '情节节点/场景标题',
                                 `description` TEXT NULL COMMENT '情节详细描述',
                                 `element_order` INT NOT NULL DEFAULT 0 COMMENT '在情节结构中的顺序',
                                 `status` VARCHAR(20) NULL COMMENT '状态 (planned, drafted, completed)',
                                 `related_chapter_id` INT UNSIGNED NULL COMMENT '关联章节ID (可选)',
                                 `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 INDEX `idx_plot_elements_plot_id` (`plot_id`),
                                 INDEX `idx_plot_elements_chapter_id` (`related_chapter_id`),
                                 FOREIGN KEY (`plot_id`) REFERENCES `plots` (`id`) ON DELETE CASCADE, -- 情节结构删除时，其元素也删除
                                 FOREIGN KEY (`related_chapter_id`) REFERENCES `chapters` (`id`) ON DELETE SET NULL -- 章节删除时，关联关系置空
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情节具体元素表';
