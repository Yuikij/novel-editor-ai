# 数据库模板
## 配置说明
```yml
spring:
  datasource:
    # mysql数据源
    username: root
    password: root
    url: jdbc:mysql://192.168.1.1:3306/database?autoReconnect=true&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=convertToNull&serverTimezone=UTC
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      #连接池初始化大小
      initial-size: 5 
      #最小空闲连接数
      min-idle: 10 
      #最大连接数
      max-active: 20 
      max-wait: 60000  
```
## 遇到的问题
* https://github.com/baomidou/mybatis-plus/issues/5243
