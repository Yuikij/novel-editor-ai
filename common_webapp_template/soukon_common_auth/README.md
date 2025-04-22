* 导入表
```sql
CREATE TABLE `user` (
  `id` bigint DEFAULT NULL,
  `username` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `password` varchar(2550) COLLATE utf8mb4_bin DEFAULT NULL,
  `enabled` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```