# iCanteen 智汇食堂后端系统

## 1. 项目简介
iCanteen 是一个面向校园场景的智慧食堂后端系统，聚焦“去哪吃、吃什么、要等多久、如何更高效下单”四类高频问题，提供食堂拥挤度感知、窗口排队时长感知、菜品推荐、评价闭环、在线下单与订单管理能力。

本项目基于 Spring Boot + MySQL + Redis + Redisson 构建，强调以下工程目标：
- 高并发下单的正确性（防超卖、防重复提交、状态流转可控）
- 缓存性能与一致性平衡（互斥锁、逻辑过期、事务后双删）
- 可演进的数据模型（评价、众包上报、推荐与积分联动）

后端主工程位于 iCanteen_backend，接口文档位于 iCanteen-api接口.openapi.json，数据库脚本位于 iCanteen.sql。

---

## 2. 核心能力

### 2.1 用户与认证
- 手机号验证码登录、密码登录
- Token 存 Redis，支持滑动续期
- 用户资料查询与更新
- 用户签到与连续签到统计（Bitmap）
- 管理员角色鉴权（role=1）

实现入口：
- UserController.java
- UserServiceImpl.java
- RefreshTokenInterceptor.java
- LoginInterceptor.java

### 2.2 食堂与窗口实时态势
- 食堂列表、食堂详情、食堂拥挤度总览
- 窗口列表、窗口详情、窗口菜品列表
- 用户上报食堂拥挤等级（带地理位置校验）
- 用户上报窗口等待时长（带地理位置校验）
- 上报频率限制，防止恶意刷数据

实现入口：
- CanteenController.java
- WindowController.java
- CanteenServiceImpl.java
- WindowServiceImpl.java

### 2.3 菜品与推荐
- 按窗口/按食堂查询菜品
- 菜品详情
- 食堂推荐菜（3道人工推荐 + 1道高分随机）
- 全局随机推荐菜
- 管理端菜品增删改与推荐配置

实现入口：
- DishController.java
- DishServiceImpl.java
- CanteenRecommendMapper.java

### 2.4 菜品评价与评分回流
- 仅允许“买过该菜且订单未取消”的用户评价
- 同用户同菜品唯一评价（插入冲突自动转更新）
- 评价后自动更新菜品平均分与评价数
- 评价数据分页缓存 + 版本号失效机制

实现入口：
- DishReviewController.java
- DishReviewServiceImpl.java

### 2.5 在线下单与订单状态机
- 下单前校验：用户位置、食堂营业时间、窗口状态、跨食堂限制
- 多菜品并发下单：按菜品加锁并排序，避免死锁
- 原子扣库存，防超卖
- 用户维度防重复提交
- 订单取消与管理员改状态，含库存回滚
- 用户端与管理端分页查询，详情缓存

实现入口：
- FoodOrderController.java
- AdminFoodOrderController.java
- FoodOrderServiceImpl.java

---

## 3. 技术栈

- 语言与运行时：Java 8+
- Web 框架：Spring Boot 2.3.12.RELEASE
- ORM：MyBatis-Plus 3.5.3
- 数据库：MySQL 5.7/8.0
- 缓存与分布式能力：Redis + Redisson
- 工具库：Hutool、Lombok
- 构建工具：Maven

依赖配置见 pom.xml。

---

## 4. 系统设计要点

### 4.1 认证与拦截链
- 刷新拦截器先执行：从请求头读取 token，加载用户并刷新 TTL
- 登录拦截器后执行：非白名单接口要求已登录
- 管理员接口通过角色二次校验

配置见 MvcConfig.java。

### 4.2 缓存策略
项目采用混合缓存策略：
- 互斥锁缓存重建：防击穿
- 逻辑过期：热点详情降级可用
- 空值缓存：防穿透
- 事务后删除 + 延迟二次删除：降低脏读窗口

核心实现：
- CacheClient.java
- CacheConsistencyHelper.java
- RedisConstants.java

### 4.3 并发控制
- Redisson 分布式锁保护订单创建、订单状态变更、库存竞争
- 多锁按 dishId 排序获取，降低死锁风险
- SQL 条件更新保证库存扣减原子性

### 4.4 地理围栏与营业时间
- Redis GEO 校验用户位置与食堂距离
- 下单距离限制 1km
- 拥挤/排队上报距离限制 300m
- 食堂营业时间解析支持多个时间段

---

## 5. 数据库设计

主脚本： iCanteen.sql

核心表：
- tb_user, tb_user_info
- tb_canteen, tb_window, tb_dish
- tb_food_order, tb_food_order_item
- tb_dish_review
- tb_crowd_report, tb_wait_time_report
- tb_canteen_recommend

其中：
- 订单状态：0 已创建，1 已接单，2 待取餐，3 已完成，4 已取消
- 评价表有唯一键约束：同用户同菜品仅一条
- 订单项与订单主表外键关联，支持级联删除

---

## 6. API 概览

完整规范请看 iCanteen-api接口.openapi.json。

### 6.1 用户接口
- /user/code
- /user/login
- /user/logout
- /user/me
- /user/info
- /user/sign
- /user/sign/count

### 6.2 食堂/窗口/菜品
- /canteen/list
- /canteen/crowd
- /canteen/{id}
- /canteen/{id}/windows
- /canteen/{id}/dishes
- /canteen/report-crowd
- /window/list
- /window/{id}
- /window/{id}/dishes
- /window/report-wait-time
- /dish/by-window
- /dish/by-canteen
- /dish/{id}
- /dish/recommend/by-canteen
- /dish/recommend/manual
- /dish/random-recommend

### 6.3 订单与评价
- /food-order
- /food-order/my
- /food-order/{id}
- /food-order/{id}/cancel
- /admin/food-order/list
- /admin/food-order/{id}
- /admin/food-order/{id}/status
- /dish-review
- /dish-review/by-dish
- /dish-review/my
- /dish-review/{id}

### 6.4 管理员用户
- /admin/user/list
- /admin/user/{id}

---

## 7. 快速启动

### 7.1 环境要求
- JDK 8 或 9
- Maven 3.6+
- MySQL 5.7+
- Redis 5+

### 7.2 初始化数据库
1. 创建数据库 iCanteen  
2. 执行 iCanteen.sql

### 7.3 修改配置
编辑 application.yaml：
- spring.datasource.url
- spring.datasource.username
- spring.datasource.password
- spring.redis.host
- spring.redis.port
- spring.redis.password
- server.port

### 7.4 启动应用
在 iCanteen_backend 执行：
- mvn clean package
- mvn spring-boot:run

默认端口：8082

---

## 8. 返回结构约定
统一响应对象定义见 Result.java。

字段说明：
- success：是否成功
- errorMsg：失败消息
- data：业务数据
- total：分页总量

---

## 9. 测试与压测

项目含基础测试类：
- NormalTest.java
- RedissonTest.java

压测相关资料：
- iCanteen.jmx
- users.csv

重点验证：
- 热点菜品并发下单不超卖
- 多菜品交叉下单无死锁

---

## 10. 项目结构

- controller
  - API 控制器层
- service
  - 业务接口
- impl
  - 业务实现
- mapper
  - 数据访问层
- utils
  - 缓存、锁、鉴权、ID、常量等工具
- resources
  - 配置与 SQL 资源
- 测试
  - 压测脚本与数据
