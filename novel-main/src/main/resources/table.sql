

-- 创建项目表
CREATE TABLE projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '项目ID，主键，自增',
    title VARCHAR(255) NOT NULL COMMENT '小说名称，非空',
    genre VARCHAR(100) COMMENT '小说类型，可为空',
    style VARCHAR(100) COMMENT '写作风格，可为空',
    synopsis TEXT COMMENT '简介，可为空',
    tags JSON COMMENT '标签，存储为JSON格式',
    target_audience VARCHAR(100) COMMENT '目标读者，可为空',
    word_count_goal BIGINT COMMENT '目标字数，可为空',
    highlights JSON COMMENT '故事爆点，存储为JSON格式',
    writing_requirements JSON COMMENT '写作要求，存储为JSON格式',
    status ENUM('draft', 'in-progress', 'completed', 'published') DEFAULT 'draft' COMMENT '项目状态，默认草稿',
    world_id BIGINT COMMENT '世界观ID，外键，可为空',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，默认当前时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间，自动更新为当前时间'
) COMMENT='存储项目信息，包括小说名称、类型、风格、简介等';




-- 创建世界观表
CREATE TABLE worlds (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '世界观ID，主键，自增',
    name VARCHAR(255) NOT NULL COMMENT '世界名称，非空',
    description TEXT COMMENT '世界描述，可为空',
    elements JSON COMMENT '世界元素，存储为JSON格式，包含类型、名称、描述、详细信息',
    notes TEXT COMMENT '备注，可为空',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，默认当前时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间，自动更新为当前时间'
) COMMENT='存储世界观信息，包括名称、描述、元素和备注';





-- 创建章节表
CREATE TABLE chapters (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '章节ID，主键，自增',
    project_id BIGINT NOT NULL COMMENT '项目ID，外键，关联projects表',
    title VARCHAR(255) NOT NULL COMMENT '章节标题，非空',
    `sort_order` INT NOT NULL COMMENT '章节顺序，非空，整数类型',
    status ENUM('draft', 'in-progress', 'completed', 'edited') DEFAULT 'draft' COMMENT '章节状态，默认草稿',
    summary VARCHAR(255) COMMENT '章节摘要，可为空',
    notes TEXT COMMENT '章节备注，可为空',
    word_count_goal BIGINT COMMENT '目标字数，可为空，章节计划字数',
    word_count BIGINT DEFAULT 0 COMMENT '统计字数，默认0，记录实际字数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，默认当前时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间，自动更新为当前时间'
) COMMENT='存储章节信息，包括标题、顺序、状态、摘要和字数信息';




-- 创建大纲情节点表
CREATE TABLE outline_plot_points (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '情节点ID，主键，自增',
    project_id BIGINT NOT NULL COMMENT '项目ID，外键，关联projects表',
    title VARCHAR(255) NOT NULL COMMENT '情节点标题，非空',
    type ENUM('起始', '发展', '高潮', '结局', '其他') NOT NULL COMMENT '情节点类型，非空',
    `sort_order` INT NOT NULL COMMENT '情节点顺序，非空，整数类型',
    description TEXT COMMENT '情节点描述，可为空',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，默认当前时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间，自动更新为当前时间'
) COMMENT='存储情节点信息，构成项目大纲，包括标题、类型、顺序和描述';

-- 创建角色表
CREATE TABLE `characters` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `project_id` int unsigned NOT NULL COMMENT '所属项目ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `role` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色定位 (protagonist, antagonist, supporting)',
  `personality` json DEFAULT NULL COMMENT '性格特点 (存储为JSON数组)',
  `background` text COLLATE utf8mb4_unicode_ci COMMENT '背景故事',
  `goals` json DEFAULT NULL COMMENT '角色目标 (存储为JSON数组)',
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色头像URL',
  `notes` text COLLATE utf8mb4_unicode_ci COMMENT '其他备注',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_characters_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说角色表';


-- 创建角色关系表
CREATE TABLE `character_relationships` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `project_id` int unsigned NOT NULL COMMENT '所属项目ID (冗余方便查询)',
  `source_character_id` int unsigned NOT NULL COMMENT '关系发起方角色ID',
  `target_character_id` int unsigned NOT NULL COMMENT '关系目标方角色ID',
  `relationship_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关系类型 (love, family, friend, rival, enemy)',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '关系描述',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_character_relationship` (`source_character_id`,`target_character_id`,`relationship_type`),
  KEY `idx_char_relationships_project` (`project_id`),
  KEY `idx_char_relationships_source` (`source_character_id`),
  KEY `idx_char_relationships_target` (`target_character_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色关系表';


-- 创建情节结构表
CREATE TABLE `plots` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '情节结构ID',
  `project_id` int unsigned NOT NULL COMMENT '所属项目ID',
  `title` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '情节/大纲标题 (如 主线, 支线A)',
  `type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结构类型 (three-act, hero-journey, custom)',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '整体描述',
  `plot_order` int NOT NULL DEFAULT '0' COMMENT '情节结构排序',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `chapter_id` int NOT NULL COMMENT '所属章节',
  PRIMARY KEY (`id`),
  KEY `idx_plots_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情节/大纲结构表';