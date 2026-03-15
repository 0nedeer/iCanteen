# iCanteen API 说明文档

## 1. 通用约定

### 1.1 基础说明

- 文档类型：OpenAPI 3.0.1
- 由于还未部署，当前环境地址为本地运行

### 1.2 鉴权方式

部分接口需要在请求头中传：

```http
authorization: <token>
```

也支持：

```http
authorization: Bearer <token>
```

### 1.3 统一返回结构

绝大多数接口都返回 `Result`：

```json
{
  "success": true,
  "errorMsg": null,
  "data": null,
  "total": null
}
```

字段说明：

| 字段       | 类型             | 说明             |
| -------- | -------------- | -------------- |
| success  | boolean        | 是否成功           |
| errorMsg | string \| null | 失败时的错误信息       |
| data     | 任意             | 业务数据，不同接口含义不同  |
| total    | number \| null | 分页总数，通常仅分页接口返回 |

### 1.4 前端统一处理建议

前端拿到响应后，建议统一这样处理：

```ts
if (!res.data.success) {
  message.error(res.data.errorMsg || '请求失败')
  return
}
return res.data.data
```

分页接口则额外读取：

```ts
const list = res.data.data
const total = res.data.total
```

### 1.5 通用枚举与字段说明

#### 手机号

约束正则：

```regex
^1[3-9]\d{9}$
```

即中国大陆 11 位手机号。

#### 日期 `birthday`

格式：`YYYY-MM-DD`

#### 时间 `createTime` / `updateTime`

格式：`date-time`，前端通常需要格式化展示。

#### 订单状态 `status`

订单相关接口常见定义：

| 值 | 含义  |
| - | --- |
| 0 | 待接单 |
| 1 | 制作中 |
| 2 | 待取餐 |
| 3 | 已完成 |
| 4 | 已取消 |

#### 上下线状态 `status`

食堂 / 窗口 / 菜品中常见为：

| 值 | 建议理解    |
| - | ------- |
| 0 | 禁用 / 下线 |
| 1 | 启用 / 上线 |

---

## 2. Paths

下面开始**严格按原文档中的接口顺序**逐个说明。

---

### 2.1 `/user/code`

### API 1. 发送登录验证码

#### 基本信息

- 方法：`POST`
- 路径：`/user/code`
- 作用：给指定手机号发送登录验证码

#### 请求参数

采用 **query 参数**：

| 参数名   | 位置    | 是否必填 | 类型     | 说明                |
| ----- | ----- | ---- | ------ | ----------------- |
| phone | query | 是    | string | 用户手机号，必须满足大陆手机号格式 |

示例：

```http
POST /user/code?phone=13456789006
```

#### 返回结果

返回统一 `Result`

通常成功时：

```json
{
  "success": true,
  "errorMsg": null,
  "data": null,
  "total": null
}
```

#### 字段与场景说明

- 本接口一般只是触发验证码发送，不返回业务对象
- `data` 通常为空
- 若发送失败，错误原因通常放在 `errorMsg`

#### 前端对接说明

1. 提交前先校验手机号格式
2. 成功后建议开始验证码倒计时（如 60 秒）
3. 倒计时期间禁用“重新发送”按钮，避免频繁请求

---

### 2.2 `/user/login`

### API 2. 登录（验证码登录 / 密码登录）

#### 基本信息

- 方法：`POST`
- 路径：`/user/login`
- 作用：用户登录，成功后返回 token

#### 请求体

`Content-Type: application/json`

请求体类型：`LoginFormDTO`

| 字段名      | 是否必填 | 类型     | 说明                     |
| -------- | ---- | ------ | ---------------------- |
| phone    | 是    | string | 手机号                    |
| code     | 条件必填 | string | 验证码，和 `password` 二选一   |
| password | 条件必填 | string | 密码，和 `code` 二选一，最少 6 位 |

#### 合法请求场景 1：验证码登录

```json
{
  "phone": "13456789006",
  "code": "685827"
}
```

#### 合法请求场景 2：密码登录

```json
{
  "phone": "13800000002",
  "password": "123456"
}
```

#### 不推荐/应避免的情况

1. `code` 和 `password` 都不传
2. `code` 和 `password` 同时都传

根据文档语义，应当是“二选一且只能选一个”。

#### 返回结果

返回统一 `Result`

业务上 `data` 为 token 字符串。

示意：

```json
{
  "success": true,
  "errorMsg": null,
  "data": "00be94d772df4bd1b84fc2a4a1b3f918",
  "total": null
}
```

#### 前端对接说明

1. 登录成功后保存 token
2. 推荐统一放入请求拦截器，自动加到 `authorization` 头里
3. 若采用 Bearer 方式，可统一拼接为：

```ts
headers.authorization = `Bearer ${token}`
```

---

### 2.3 `/user/me`

### API 3. 获取当前登录用户简要信息

#### 基本信息

- 方法：`GET`
- 路径：`/user/me`
- 作用：获取当前登录用户的简要信息

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### 返回结果

文档描述是：`Result(data=UserDTO)`

预期 `data` 结构：

| 字段名      | 类型     | 说明       |
| -------- | ------ | -------- |
| id       | number | 当前用户 ID  |
| nickName | string | 用户昵称     |
| icon     | string | 用户头像 URL |

示例：

```json
{
  "success": true,
  "data": {
    "id": 1,
    "nickName": "Tom",
    "icon": "/imgs/icons/default1.png"
  }
}
```

#### 前端对接说明

1. 适合在页面初始化时拉取（后端代码已设置网页拦截器，但效果需实践测试）
2. 可用于顶部导航栏展示头像、昵称
3. 该接口可用于校验 token 是否仍有效
4. 注意字段名是 `nickName`，不是 `nickname`

---

### 2.4 `/user/info`（GET）

### API 4. 获取当前登录用户详情

#### 基本信息

- 方法：`GET`
- 路径：`/user/info`
- 作用：获取当前登录用户的完整资料

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### 返回结果

返回：`Result(data=UserProfileDTO)`

`data` 字段说明：

| 字段名       | 类型              | 说明                    |
| --------- | --------------- | --------------------- |
| userId    | number          | 用户 ID                 |
| phone     | string          | 手机号                   |
| password  | string          | 密码字段（文档中存在，不建议前端直接展示） |
| nickname  | string          | 昵称                    |
| icon      | string          | 头像                    |
| city      | string          | 城市                    |
| introduce | string          | 个性简介                  |
| gender    | boolean \| null | 性别                    |
| birthday  | string \| null  | 生日，格式 `YYYY-MM-DD`    |
| credits   | number          | 积分                    |

#### 前端对接说明

1. 这个接口适合用于“个人中心详情页”初始化
2. 修改资料前可先调用该接口回填表单
3. 如果真的返回 `password`，前端**不要明文展示**
4. 修改密码时不要把后端返回的 `password` 直接回填到密码框

---

### 2.5 `/user/info`（PUT）

### API 5. 修改当前登录用户信息

#### 基本信息

- 方法：`PUT`
- 路径：`/user/info`
- 作用：修改当前登录用户资料

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### 请求体

类型：`UserInfoUpdateDTO`

| 字段名       | 是否必填 | 类型              | 说明                 |
| --------- | ---- | --------------- | ------------------ |
| phone     | 否    | string          | 新手机号，需满足手机号格式      |
| password  | 否    | string          | 新密码，至少 6 位         |
| nickname  | 否    | string          | 新昵称，最大 32 字符       |
| icon      | 否    | string          | 新头像地址，最大 255 字符    |
| city      | 否    | string          | 城市，最大 64 字符        |
| introduce | 否    | string          | 个性简介，最大 256 字符     |
| gender    | 否    | boolean \| null | 性别                 |
| birthday  | 否    | string \| null  | 生日，格式 `YYYY-MM-DD` |

#### 请求示例

```json
{
  "birthday": "2003-12-31",
  "city": "Shenzhen",
  "gender": false,
  "phone": "13456789008",
  "icon": "/imgs/icons/default2.png",
  "introduce": "hihihi!",
  "nickname": "user_abc",
  "password": "321cba"
}
```

#### 返回结果

返回统一 `Result`

通常成功时 `data` 为空。

#### 字段说明与不同情况

这是一个“部分更新”接口：

- 可以只改一个字段
- 也可以多个字段一起改
- 不改的字段不要传!!!

#### 前端对接说明

1. 提交前做前端校验，减少无效请求
2. 修改手机号时，后端会校验唯一性；若手机号重复，可能返回失败
3. 修改密码成功后是否要求重新登录，文档没写，需要和后端确认

---

### 2.6 `/user/logout`

### API 6. 退出登录

#### 基本信息

- 方法：`POST`
- 路径：`/user/logout`
- 作用：退出登录

#### 请求头

文档中 `authorization` **不是必填**。

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 否    | string | 登录 token |

#### 返回结果

统一 `Result`

#### 前端对接说明

1. 无论后端是否成功，都建议前端清除本地 token
2. 同时清空用户信息缓存、权限状态
3. 然后跳回登录页或首页
4. 即使本地 token 已失效，也可以执行本地退出流程

---

### 2.7 `/user/sign`

### API 7. 用户签到

#### 基本信息

- 方法：`POST`
- 路径：`/user/sign`
- 作用：执行当天签到

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### 返回结果

统一 `Result`

#### 接口语义说明

根据文档描述，这个接口用于用户每日签到。

常见业务效果可能包括：

- 标记今天已签到
- 刷新连续签到天数

#### 前端对接说明

1. 签到按钮点击后调用
2. 成功后建议刷新签到状态、连续签到天数
3. 已签到状态应在 UI 上禁用重复点击

---

### 2.8 `/user/sign/count`

### API 8. 查询连续签到天数

#### 基本信息

- 方法：`GET`
- 路径：`/user/sign/count`
- 作用：获取当前用户连续签到天数

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### 返回结果

`Result(data=number)`

例如：

```json
{
  "success": true,
  "data": 5,
}
```

#### 前端对接说明

- `data` 直接当作数字使用
- 可用于签到页、个人中心、积分页展示“已连续签到 N 天”

---

### 2.9 `/admin/user/list`

### API 9. 管理员分页查询用户列表

#### 基本信息

- 方法：`GET`
- 路径：`/admin/user/list`
- 作用：管理员按条件分页查询用户列表

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### Query 参数

| 参数名      | 是否必填 | 类型      | 说明                 |
| -------- | ---- | ------- | ------------------ |
| current  | 否    | integer | 当前页码，默认 1          |
| pageSize | 否    | integer | 每页条数，默认 10         |
| phone    | 否    | string  | 按手机号筛选             |
| nickname | 否    | string  | 按昵称筛选              |
| role     | 否    | integer | 按角色筛选，0 普通用户，1 管理员 |

#### 返回结果

`Result(data=AdminUserDTO[], total=总数)`

每个用户对象字段：

| 字段名       | 类型              | 说明    |
| --------- | --------------- | ----- |
| userId    | number          | 用户 ID |
| phone     | string          | 手机号   |
| password  | string          | 密码字段  |
| role      | 0 \| 1          | 用户角色  |
| nickname  | string          | 昵称    |
| icon      | string          | 头像    |
| city      | string          | 城市    |
| introduce | string          | 简介    |
| gender    | boolean \| null | 性别    |
| birthday  | string \| null  | 生日    |
| credits   | number \| null  | 积分    |

#### 前端对接说明

1. `data` 是当前页列表
2. `total` 是总条数，用于分页器
3. 建议把筛选条件和分页条件统一保存在页面状态中
4. `password` 不建议在管理端表格中直接展示（可设置为鼠标移动到该位置/鼠标点击后展示）

---

### 2.10 `/admin/user/{id}`（GET）

### API 10. 管理员查看用户详情

#### 基本信息

- 方法：`GET`
- 路径：`/admin/user/{id}`
- 作用：管理员查看指定用户详情

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 用户 ID |

#### 返回结果

`Result(data=AdminUserDTO)`

每个用户对象字段：

| 字段名       | 类型              | 说明    |
| --------- | --------------- | ----- |
| userId    | number          | 用户 ID |
| phone     | string          | 手机号   |
| password  | string          | 密码字段  |
| role      | 0 \| 1          | 用户角色  |
| nickname  | string          | 昵称    |
| icon      | string          | 头像    |
| city      | string          | 城市    |
| introduce | string          | 简介    |
| gender    | boolean \| null | 性别    |
| birthday  | string \| null  | 生日    |
| credits   | number \| null  | 积分    |

#### 前端对接说明

- 可用于后台用户详情抽屉/详情页
- 也可用于编辑前回填数据

---

### 2.11 `/admin/user/{id}`（PUT）

### API 11. 管理员修改用户信息

#### 基本信息

- 方法：`PUT`
- 路径：`/admin/user/{id}`
- 作用：管理员修改指定用户信息

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明        |
| --- | ---- | ----- | --------- |
| id  | 是    | int64 | 被修改的用户 ID |

#### 请求体

类型：`AdminUserUpdateDTO`

| 字段名       | 是否必填 | 类型              | 说明              |
| --------- | ---- | --------------- | --------------- |
| phone     | 否    | string          | 手机号             |
| password  | 否    | string          | 密码，至少 6 位       |
| role      | 否    | integer         | 角色，0 普通用户，1 管理员 |
| nickname  | 否    | string          | 昵称，最大 32 字符     |
| icon      | 否    | string          | 头像地址，最大 255 字符  |
| city      | 否    | string          | 城市，最大 64 字符     |
| introduce | 否    | string          | 简介，最大 256 字符    |
| gender    | 否    | boolean \| null | 性别              |
| birthday  | 否    | string \| null  | 生日              |
| credits   | 否    | integer         | 积分，最小 0         |

#### 返回结果

统一 `Result`

#### 字段说明与不同情况

- 用户 ID 通过 path 参数指定
- body 里只传要修改的字段
- 可以只改积分、只改角色，或多字段一起修改

#### 前端对接说明

1. 修改角色属于高风险操作，建议加二次确认
2. 修改手机号会触发唯一约束报错
3. 修改积分时建议限制为非负整数
4. 编辑表单要区分“未修改”和“清空字段”

---

### 2.12 `/canteen/list`

### API 12. 查询所有食堂

#### 基本信息

- 方法：`GET`
- 路径：`/canteen/list`
- 作用：查询全部食堂列表

#### 返回结果

`Result(data=Canteen[])`

每个食堂对象字段：

| 字段名        | 类型     | 说明               |
| ---------- | ------ | ---------------- |
| id         | number | 食堂 ID            |
| name       | string | 食堂名称             |
| address    | string | 食堂地址             |
| images     | string | 图片字段，类型是字符串，不是数组 |
| x          | number | 坐标 X             |
| y          | number | 坐标 Y             |
| crowdLevel | number | 拥挤等级             |
| crowdScore | number | 拥挤评分/拥挤分值        |
| openHours  | string | 营业时间             |
| status     | number | 状态               |

#### 原文档说明

该接口返回结果按拥挤程度从轻到重排序。

#### 前端对接说明

1. 可直接用于首页食堂列表
2. 不建议前端再自行按拥挤度重排，除非产品有新的排序规则
3. `images` 是字符串

---

### 2.13 `/canteen/crowd`

### API 13. 查询所有食堂拥挤程度

#### 基本信息

- 方法：`GET`
- 路径：`/canteen/crowd`
- 作用：获取所有食堂的拥挤度简要信息

#### 返回结果

`Result(data=CanteenCrowdDTO[])`

字段：

| 字段名        | 类型     | 说明    |
| ---------- | ------ | ----- |
| canteenId  | number | 食堂 ID |
| name       | string | 食堂名称  |
| crowdLevel | number | 拥挤等级  |
| crowdScore | number | 拥挤分值  |

#### 前端对接说明

- 适合做大屏、首页简版状态、拥挤榜单
- 如果页面不需要完整食堂信息，可优先用该接口减少字段处理量

---

### 2.14 `/canteen/{id}`（GET）

### API 14. 查询单个食堂详情

#### 基本信息

- 方法：`GET`
- 路径：`/canteen/{id}`
- 作用：获取某个食堂的完整详情

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 食堂 ID |

#### 返回结果

`Result(data=Canteen)`

字段含义同 API 12。

#### 前端对接说明

- 适合食堂详情页初始化
- 若页面还需要窗口列表，可继续调 `/canteen/{id}/windows`

---

### 2.15 `/canteen/{id}/windows`

### API 15. 查询某食堂下所有窗口

#### 基本信息

- 方法：`GET`
- 路径：`/canteen/{id}/windows`
- 作用：获取某个食堂下全部窗口

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 食堂 ID |

#### 返回结果

`Result(data=Window[])`

`Window` 字段：

| 字段名           | 类型     | 说明       |
| ------------- | ------ | -------- |
| id            | number | 窗口 ID    |
| canteenId     | number | 所属食堂 ID  |
| name          | string | 窗口名称     |
| description   | string | 窗口描述     |
| waitTime      | number | 当前预计等待时长 |
| waitTimeLevel | number | 排队等级     |
| status        | number | 状态       |
| sort          | number | 排序值      |

#### 前端对接说明

1. 可直接展示为食堂详情页中的窗口列表
2. `waitTime` 理解为分钟数

---

### 2.16 `/canteen/{id}/dishes`

### API 16. 查询某食堂今日菜品

#### 基本信息

- 方法：`GET`
- 路径：`/canteen/{id}/dishes`
- 作用：获取某个食堂今日供应的菜品

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 食堂 ID |

#### 返回结果

`Result(data=Dish[])`

`Dish` 字段：

| 字段名         | 类型     | 说明            |
| ----------- | ------ | ------------- |
| id          | number | 菜品 ID         |
| windowId    | number | 所属窗口 ID       |
| name        | string | 菜名            |
| description | string | 菜品描述          |
| image       | string | 菜品图片          |
| price       | number | 单价            |
| quantity    | number | 库存            |
| tags        | string | 标签字符串         |
| avgRating   | number | 平均评分          |
| ratingCount | number | 评分人数          |
| status      | number | 状态            |
| isToday     | number | 是否今日供应，通常 0/1 |

#### 前端对接说明

1. 文档名称强调“今日菜品”，前端应理解为今日可供的菜品列表
2. `tags` 是字符串而不是数组，展示前要确认分隔规则
3. 如果库存字段 `quantity` 为 0，需在前端做售罄展示

---

### 2.17 `/canteen/report-crowd`

### API 17. 上报食堂拥挤度

#### 基本信息

- 方法：`POST`
- 路径：`/canteen/report-crowd`
- 作用：用户上报当前食堂拥挤情况

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### Query 参数

| 参数名        | 是否必填 | 类型      | 说明                  |
| ---------- | ---- | ------- | ------------------- |
| canteenId  | 是    | int64   | 食堂 ID               |
| crowdLevel | 是    | integer | 拥挤等级：0 空闲，1 适中，2 拥挤 |

#### 返回结果

统一 `Result`

#### 前端对接说明

1. 可做成“我来反馈拥挤度”的交互
2. 提交值只能是 0/1/2 （可设计为红绿灯形式，并标小字说明，供用户选择点击）
3. 成功后可刷新食堂拥挤状态数据
4. 为避免重复提交，按钮提交后建议加短暂 loading（若频繁操作，后端会返回"errorMsg"）

---

### 2.18 `/canteen`（POST）

### API 18. 新增食堂（管理员）

#### 基本信息

- 方法：`POST`
- 路径：`/canteen`
- 作用：管理员新增食堂

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### 请求体

类型：`CanteenCreateDTO`

| 字段名       | 是否必填 | 类型      | 说明   |
| --------- | ---- | ------- | ---- |
| name      | 是    | string  | 食堂名称 |
| address   | 是    | string  | 食堂地址 |
| images    | 否    | string  | 食堂图片 |
| x         | 是    | number  | 坐标 X |
| y         | 是    | number  | 坐标 Y |
| openHours | 否    | string  | 营业时间 |
| status    | 否    | integer | 状态   |

#### 返回结果

`Result(data=id)`

即新增成功后，`data` 为新食堂 ID。

#### 前端对接说明

1. 创建成功后可根据返回的 id 跳转详情页或编辑页
2. 坐标字段前端最好明确由地图选点或经纬度输入得到

---

### 2.19 `/canteen`（PUT）

### API 19. 修改食堂（管理员）

#### 基本信息

- 方法：`PUT`
- 路径：`/canteen`
- 作用：管理员修改食堂信息

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### 请求体

类型：`CanteenUpdateDTO`

| 字段名       | 是否必填 | 类型      | 说明    |
| --------- | ---- | ------- | ----- |
| id        | 是    | int64   | 食堂 ID |
| name      | 否    | string  | 食堂名称  |
| address   | 否    | string  | 地址    |
| images    | 否    | string  | 图片    |
| x         | 否    | number  | 坐标 X  |
| y         | 否    | number  | 坐标 Y  |
| openHours | 否    | string  | 营业时间  |
| status    | 否    | integer | 状态    |

#### 返回结果

统一 `Result`

#### 前端对接说明

1. 这是“带 id 的部分更新接口”
2. 必须传 `id`
3. 其他字段只传要改的内容即可
4. 若编辑页是整表单提交，也可全部字段一起提交

---

### 2.20 `/canteen/{id}`（DELETE）

### API 20. 删除食堂（管理员）

#### 基本信息

- 方法：`DELETE`
- 路径：`/canteen/{id}`
- 作用：管理员删除指定食堂

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 食堂 ID |

#### 返回结果

统一 `Result`

#### 前端对接说明

1. 删除前建议强制二次确认
2. 备注：数据库未级联删除窗口/菜品
3. 删除成功后应刷新列表页

---

### 2.21 `/window/list`

### API 21. 查询某食堂的窗口列表

#### 基本信息

- 方法：`GET`
- 路径：`/window/list`
- 作用：通过 query 参数查询某食堂下的窗口列表

#### Query 参数

| 参数名       | 是否必填 | 类型    | 说明    |
| --------- | ---- | ----- | ----- |
| canteenId | 是    | int64 | 食堂 ID |

#### 返回结果

`Result(data=Window[])`

字段说明同 API 15。

#### 与 API 15 的关系

这个接口和 `/canteen/{id}/windows` 语义一致，都是查“某食堂下的窗口”。

#### 前端对接说明

1. 两个接口建议选一个主用，避免重复维护
2. 若项目更偏 RESTful，可优先用 `/canteen/{id}/windows`
3. 若已有公共筛选组件基于 query 参数，也可用本接口

---

### 2.22 `/window/{id}`（GET）

### API 22. 查询窗口详情

#### 基本信息

- 方法：`GET`
- 路径：`/window/{id}`
- 作用：查询指定窗口详情

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 窗口 ID |

#### 返回结果

`Result(data=Window)`

字段说明同 API 15。

#### 前端对接说明

- 可用于窗口详情页
- 若页面同时需要该窗口菜品，可继续调用 `/window/{id}/dishes`

---

### 2.23 `/window/{id}/dishes`

### API 23. 查询某窗口的所有菜品

#### 基本信息

- 方法：`GET`
- 路径：`/window/{id}/dishes`
- 作用：查询某个窗口下的全部菜品

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 窗口 ID |

#### 返回结果

`Result(data=Dish[])`

字段说明同 API 16。

#### 前端对接说明

- 可用于窗口详情页中的菜品列表
- 只展示今日供应

---

### 2.24 `/window/report-wait-time`

### API 24. 上报窗口排队时长

#### 基本信息

- 方法：`POST`
- 路径：`/window/report-wait-time`
- 作用：用户上报窗口当前排队时长

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### Query 参数

| 参数名      | 是否必填 | 类型      | 说明        |
| -------- | ---- | ------- | --------- |
| windowId | 是    | int64   | 窗口 ID     |
| waitTime | 是    | integer | 等待时长（min），最小 0 |

#### 原文档说明

- 同一用户对同一窗口 **30 分钟内只能上报一次**

#### 返回结果

统一 `Result`

#### 前端对接说明

1. `waitTime` 建议只允许输入非负整数
2. 成功上报后可刷新窗口详情
3. 若短时间重复上报，后端可能返回失败提示
4. 前端可以在成功后给按钮增加短期禁用状态，优化体验

---

### 2.25 `/window`（POST）

### API 25. 新增窗口（管理员）

#### 基本信息

- 方法：`POST`
- 路径：`/window`
- 作用：管理员新增窗口

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### 请求体

类型：`WindowCreateDTO`

| 字段名           | 是否必填 | 类型      | 说明      |
| ------------- | ---- | ------- | ------- |
| canteenId     | 是    | int64   | 所属食堂 ID |
| name          | 是    | string  | 窗口名称    |
| description   | 否    | string  | 窗口描述    |
| waitTime      | 否    | integer | 当前等待时长  |
| waitTimeLevel | 否    | integer | 等待等级    |
| status        | 否    | integer | 状态      |
| sort          | 否    | integer | 排序值     |

#### 返回结果

`Result(data=id)`

新增成功后返回窗口 ID。

#### 前端对接说明

1. `canteenId` 必须明确指定所属食堂
2. `sort` 可用于后台拖拽排序后的持久化
3. 新建成功后可跳转到窗口详情或菜品管理页

---

### 2.26 `/window`（PUT）

### API 26. 修改窗口（管理员）

#### 基本信息

- 方法：`PUT`
- 路径：`/window`
- 作用：管理员修改窗口信息

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### 请求体

类型：`WindowUpdateDTO`

| 字段名           | 是否必填 | 类型      | 说明      |
| ------------- | ---- | ------- | ------- |
| id            | 是    | int64   | 窗口 ID   |
| canteenId     | 否    | int64   | 所属食堂 ID |
| name          | 否    | string  | 名称      |
| description   | 否    | string  | 描述      |
| waitTime      | 否    | integer | 等待时长    |
| waitTimeLevel | 否    | integer | 等待等级    |
| status        | 否    | integer | 状态      |
| sort          | 否    | integer | 排序值     |

#### 返回结果

统一 `Result`

#### 前端对接说明

1. 必须带 `id`
2. 支持部分字段更新
3. 若编辑页包含完整表单，也可整体提交

---

### 2.27 `/window/{id}`（DELETE）

### API 27. 删除窗口（管理员）

#### 基本信息

- 方法：`DELETE`
- 路径：`/window/{id}`
- 作用：管理员删除窗口

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 窗口 ID |

#### 返回结果

统一 `Result`

#### 前端对接说明

- 删除前建议确认该窗口下是否还有菜品
- 删除成功后刷新窗口列表

---

### 2.28 `/dish/random-recommend`

### API 28. 获取今日随机推荐菜式

#### 基本信息

- 方法：`GET`
- 路径：`/dish/random-recommend`
- 作用：获取一道随机推荐菜品

#### 原文档说明

返回一道“今日可售且高评分”的推荐菜式。

#### 返回结果

`Result(data=Dish)`

字段说明同 API 16。

#### 前端对接说明

- 适合首页推荐卡片、弹窗推荐、今日一荐

---

### 2.29 `/dish/{id}`（GET）

### API 29. 查询菜品详情

#### 基本信息

- 方法：`GET`
- 路径：`/dish/{id}`
- 作用：查询单个菜品详情

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 菜品 ID |

#### 返回结果

`Result(data=Dish)`

字段说明同 API 16。

#### 前端对接说明

- 可用于菜品详情页、下单确认弹窗、菜品编辑回填

---

### 2.30 `/dish/recommend/by-canteen`

### API 30. 获取某食堂推荐菜式

#### 基本信息

- 方法：`GET`
- 路径：`/dish/recommend/by-canteen`
- 作用：查询某个食堂的推荐菜品列表

#### Query 参数

| 参数名       | 是否必填 | 类型    | 说明    |
| --------- | ---- | ----- | ----- |
| canteenId | 是    | int64 | 食堂 ID |

#### 原文档说明

返回 4 道菜：

1. 该食堂最高分菜式中随机 1 道
2. 管理员手动推荐 3 道

#### 返回结果

`Result(data=Dish[])`

#### 前端对接说明

1. 虽然文档描述是 4 道，但前端不要把长度写死为 4
2. 若后端数据不足，可能少于 4 道 (一般来说，数据会尽量避免这种情况，若发生，后端会报错)
3. 可用于食堂详情页推荐区

---

### 2.31 `/dish/recommend/manual`

### API 31. 设置手动推荐菜式（管理员）

#### 基本信息

- 方法：`POST`
- 路径：`/dish/recommend/manual`
- 作用：管理员为某食堂设置手动推荐菜式

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### 请求体

类型：`DishManualRecommendDTO`

| 字段名       | 是否必填 | 类型      | 说明                |
| --------- | ---- | ------- | ----------------- |
| canteenId | 是    | int64   | 食堂 ID             |
| dishIds   | 是    | int64[] | 菜品 ID 数组，必须恰好 3 个 |

#### 特殊约束

`dishIds` 有如下约束：

- 最少 3 个
- 最多 3 个

也就是：**只能传 3 个**。

#### 示例

```json
{
  "canteenId": 2,
  "dishIds": [7, 9, 11]
}
```

#### 返回结果

统一 `Result`

#### 前端对接说明

1. 选择器需要限制最多选择 3 个
2. 提交前要校验是否正好 3 个
3. 最好校验 3 个菜品是否都属于同一个食堂

---

### 2.32 `/dish`（POST）

### API 32. 新增菜品（管理员）

#### 基本信息

- 方法：`POST`
- 路径：`/dish`
- 作用：管理员新增菜品

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### 请求体

类型：`DishCreateDTO`

| 字段名         | 是否必填 | 类型      | 说明            |
| ----------- | ---- | ------- | ------------- |
| windowId    | 是    | int64   | 所属窗口 ID       |
| name        | 是    | string  | 菜品名称          |
| price       | 是    | number  | 单价            |
| description | 否    | string  | 描述            |
| image       | 否    | string  | 图片            |
| quantity    | 否    | integer | 库存，最小 0       |
| tags        | 否    | string  | 标签            |
| status      | 否    | integer | 状态            |
| isToday     | 否    | integer | 是否今日菜品，通常 0/1 |

#### 返回结果

`Result(data=id)`

即新增成功返回菜品 ID。

#### 前端对接说明

1. `price` 建议限制为非负数
2. `quantity` 应限制为非负整数
3. `isToday` 通常做成开关或单选
4. `tags` 字段格式需和后端确认

---

### 2.33 `/dish`（PUT）

### API 33. 修改菜品（管理员）

#### 基本信息

- 方法：`PUT`
- 路径：`/dish`
- 作用：管理员修改菜品

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### 请求体

类型：`DishUpdateDTO`

| 字段名         | 是否必填 | 类型      | 说明      |
| ----------- | ---- | ------- | ------- |
| id          | 是    | int64   | 菜品 ID   |
| windowId    | 否    | int64   | 所属窗口 ID |
| name        | 否    | string  | 菜名      |
| price       | 否    | number  | 价格      |
| description | 否    | string  | 描述      |
| image       | 否    | string  | 图片      |
| quantity    | 否    | integer | 库存      |
| tags        | 否    | string  | 标签      |
| status      | 否    | integer | 状态      |
| isToday     | 否    | integer | 是否今日菜品  |

#### 返回结果

统一 `Result`

#### 前端对接说明

- 与新增类似，但必须带 `id`
- 支持部分更新
- 若修改库存，前端应注意与订单系统联动时的业务提示

---

### 2.34 `/dish/{id}`（DELETE）

### API 34. 删除菜式（管理员）

#### 基本信息

- 方法：`DELETE`
- 路径：`/dish/{id}`
- 作用：管理员删除指定菜品

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 菜品 ID |

#### 返回结果

统一 `Result`

#### 前端对接说明

- 删除成功后刷新菜品列表
- 若该菜品已有关联订单/评价，删除策略需要联调确认

---

### 2.35 `/food-order`

### API 35. 用户下单

#### 基本信息

- 方法：`POST`
- 路径：`/food-order`
- 作用：用户创建订单

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### 请求体

类型：`FoodOrderCreateDTO`

字段：

| 字段名    | 是否必填 | 类型                       | 说明           |
| ------ | ---- | ------------------------ | ------------ |
| items  | 是    | FoodOrderItemCreateDTO[] | 订单项数组，至少 1 项 |
| remark | 否    | string                   | 订单备注         |

其中订单项 `FoodOrderItemCreateDTO`：

| 字段名      | 是否必填 | 类型      | 说明        |
| -------- | ---- | ------- | --------- |
| dishId   | 是    | int64   | 菜品 ID     |
| quantity | 是    | integer | 购买数量，最小 1 |

#### 请求示例（来自文档）

```json
{
  "items": [
    { "dishId": 17, "quantity": 2 },
    { "dishId": 23, "quantity": 1 }
  ],
  "remark": "不要葱和香菜，谢谢！",
  "longitude": 120.151506,
  "latitude": 30.333422
}
```

#### 返回结果

`Result(data=orderId)`

即 `data` 为新订单 ID。

#### 前端对接说明

1. `items` 至少 1 项
2. 每项 `quantity` 至少 1
3. 下单按钮必须防重复点击
4. 在请求完成前应保持 loading，避免重复下单
5. 业务需要定位信息，请前端请求权限并获取后返回

---

### 2.36 `/food-order/my`

### API 36. 查询我的订单列表

#### 基本信息

- 方法：`GET`
- 路径：`/food-order/my`
- 作用：用户分页查询自己的订单列表

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### Query 参数

| 参数名      | 是否必填 | 类型      | 说明      |
| -------- | ---- | ------- | ------- |
| current  | 否    | integer | 当前页     |
| pageSize | 否    | integer | 每页条数    |
| status   | 否    | integer | 按订单状态筛选 |

#### 返回结果

`Result(data=FoodOrderSimpleDTO[], total=总数)`

订单简要字段：

| 字段名         | 类型     | 说明    |
| ----------- | ------ | ----- |
| id          | number | 订单 ID |
| orderNo     | string | 订单编号  |
| userId      | number | 用户 ID |
| totalAmount | number | 总金额   |
| totalCount  | number | 菜品总数量 |
| status      | 0\~4   | 订单状态  |
| pickupCode  | string | 取餐码   |
| createTime  | string | 创建时间  |
| updateTime  | string | 更新时间  |

#### 前端对接说明

1. `data` 作为当前页订单数组
2. `total` 用于分页
3. 可根据 `status` 做 tab 分类，如待取餐 / 已完成 / 已取消
4. 取餐码可在订单卡片中重点展示

---

### 2.37 `/food-order/{id}`（GET）

### API 37. 查询订单详情

#### 基本信息

- 方法：`GET`
- 路径：`/food-order/{id}`
- 作用：查询某个订单的完整详情

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 订单 ID |

#### 返回结果

`Result(data=FoodOrderDetailDTO)`

在 `FoodOrderSimpleDTO` 基础上，扩展字段：

| 字段名          | 类型                 | 说明    |
| ------------ | ------------------ | ----- |
| remark       | string             | 用户备注  |
| cancelReason | string             | 取消原因  |
| items        | FoodOrderItemDTO[] | 订单项列表 |

其中 `FoodOrderItemDTO`：

| 字段名       | 类型     | 说明     |
| --------- | ------ | ------ |
| id        | number | 订单项 ID |
| dishId    | number | 菜品 ID  |
| dishName  | string | 菜品名称   |
| dishImage | string | 菜品图片   |
| windowId  | number | 窗口 ID  |
| price     | number | 下单时单价  |
| quantity  | number | 数量     |
| amount    | number | 当前项金额  |
| status    | number | 订单项状态  |

#### 前端对接说明

1. 订单详情页建议展示：订单号、状态、取餐码、时间、备注、菜品列表
2. 若订单已取消且有 `cancelReason`，应展示取消原因
3. `price` 和 `amount` 建议保留金额格式化

---

### 2.38 `/food-order/{id}/cancel`

### API 38. 用户取消订单

#### 基本信息

- 方法：`PUT`
- 路径：`/food-order/{id}/cancel`
- 作用：用户取消自己的订单

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 订单 ID |

#### Query 参数

| 参数名          | 是否必填 | 类型     | 说明   |
| ------------ | ---- | ------ | ---- |
| cancelReason | 否    | string | 取消原因 |

#### 返回结果

统一 `Result`

#### 字段与情况说明

该接口是否允许取消，通常取决于订单当前状态。以下状态下用户可取消：

- 0待接单：通常允许取消
- 1制作中：可能允许，也可能不允许

#### 前端对接说明

1. 建议只在允许取消的状态下显示“取消订单”按钮
2. 可弹窗让用户填写 `cancelReason`
3. 调用成功后刷新订单详情或订单列表

---

### 2.39 `/admin/food-order/list`

### API 39. 管理员分页查询订单列表

#### 基本信息

- 方法：`GET`
- 路径：`/admin/food-order/list`
- 作用：管理员分页查询订单列表

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### Query 参数

| 参数名      | 是否必填 | 类型      | 说明        |
| -------- | ---- | ------- | --------- |
| current  | 否    | integer | 当前页       |
| pageSize | 否    | integer | 每页大小      |
| status   | 否    | integer | 按状态筛选     |
| userId   | 否    | int64   | 按用户 ID 筛选 |
| windowId | 否    | Long    | 按窗口 ID 筛选 |

#### 返回结果

`Result(data=FoodOrderSimpleDTO[], total=总数)`

字段说明同 API 36。

#### 前端对接说明

1. 用于后台订单列表页
2. `total` 用于分页
3. 可结合 `status` 实现订单筛选 tab

---

### 2.40 `/admin/food-order/{id}`（GET）

### API 40. 管理员查询订单详情

#### 基本信息

- 方法：`GET`
- 路径：`/admin/food-order/{id}`
- 作用：管理员查看订单详情

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 订单 ID |

#### 返回结果

`Result(data=FoodOrderDetailDTO)`

字段说明同 API 37。

#### 前端对接说明

- 适合后台订单详情页或抽屉页
- 可显示订单项、取消原因、取餐码、用户信息等

---

### 2.41 `/admin/food-order/{id}/status`

### API 41. 管理员更新订单状态

#### 基本信息

- 方法：`PUT`
- 路径：`/admin/food-order/{id}/status`
- 作用：管理员修改订单状态

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明        |
| ------------- | ------ | ---- | ------ | --------- |
| authorization | header | 是    | string | 管理员 token |

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 订单 ID |

#### 请求体

类型：`FoodOrderStatusUpdateDTO`

| 字段名          | 是否必填 | 类型      | 说明                                 |
| ------------ | ---- | ------- | ---------------------------------- |
| status       | 是    | integer | 订单状态：0 待接单，1 制作中，2 待取餐，3 已完成，4 已取消 |
| cancelReason | 否    | string  | 若改为取消，可填写取消原因                      |

#### 返回结果

统一 `Result`

#### 前端对接说明

1. 后台最好不要让任意状态互相跳转，应按业务流程限制按钮(0->1/4, 1->2/4, 2->3)
2. 若设置为“已取消”，建议同时填写 `cancelReason`
3. 状态更新成功后刷新详情和列表

---

### 2.42 `/dish-review`

### API 42. 提交菜品评价

#### 基本信息

- 方法：`POST`
- 路径：`/dish-review`
- 作用：用户提交菜品评价

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### 原文档语义

需要：

- 已登录
- 已下单

即不是任意用户都能评价，后端应会校验购买记录。

#### 请求体

类型：`DishReviewSubmitDTO`

| 字段名     | 是否必填 | 类型      | 说明                 |
| ------- | ---- | ------- | ------------------ |
| dishId  | 是    | int64   | 菜品 ID              |
| rating  | 是    | integer | 评分，schema 约束为 1\~5 |
| content | 是    | string  | 评价内容，长度 1\~512     |
| tags    | 否    | string  | 标签                 |
| images  | 否    | string  | 图片                 |

#### 返回结果

统一 `Result`

#### 前端对接说明

1. 提交前校验 `rating` 是否在 1\~5 内 (五星评价)
2. `content` 不能为空
3. `images` 是字符串，不是数组，需确认格式
4. 建议只对已完成订单中的菜品开放评价入口

---

### 2.43 `/dish-review/by-dish`

### API 43. 分页查看某道菜品评价

#### 基本信息

- 方法：`GET`
- 路径：`/dish-review/by-dish`
- 作用：分页查询某道菜品的评价列表

#### 请求头

原文档要求：

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

也就是说，这个接口虽然看起来像公开评价列表，但文档要求登录。

#### Query 参数

| 参数名     | 是否必填 | 类型      | 说明       |
| ------- | ---- | ------- | -------- |
| dishId  | 是    | int64   | 菜品 ID    |
| current | 否    | integer | 当前页，默认 1 |

#### 返回结果

`Result(data=DishReview[], total=总数)`

`DishReview` 字段：

| 字段名     | 类型     | 说明    |
| ------- | ------ | ----- |
| id      | number | 评价 ID |
| dishId  | number | 菜品 ID |
| userId  | number | 用户 ID |
| rating  | number | 评分    |
| content | string | 评价内容  |
| tags    | string | 标签    |
| images  | string | 图片    |

#### 前端对接说明

1. 用于菜品详情页评价列表
2. `total` 用于分页或“查看更多”
3. `images` 字段需先确认格式再决定是否转数组渲染
4. 如果产品希望游客可见评价，需要和后端确认鉴权要求是否可调整

---

### 2.44 `/dish-review/my`

### API 44. 分页查看我发表过的评价

#### 基本信息

- 方法：`GET`
- 路径：`/dish-review/my`
- 作用：分页查询当前用户自己发过的评价

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### Query 参数

| 参数名     | 是否必填 | 类型      | 说明       |
| ------- | ---- | ------- | -------- |
| current | 否    | integer | 当前页，默认 1 |

#### 返回结果

`Result(data=DishReview[], total=总数)`

字段说明同 API 43。

#### 前端对接说明

- 适合“我的评价”页面
- 删除按钮通常配合下一个接口使用

---

### 2.45 `/dish-review/{id}`（DELETE）

### API 45. 删除我发表过的评价

#### 基本信息

- 方法：`DELETE`
- 路径：`/dish-review/{id}`
- 作用：删除当前用户自己发表的评价

#### 请求头

| 参数名           | 位置     | 是否必填 | 类型     | 说明       |
| ------------- | ------ | ---- | ------ | -------- |
| authorization | header | 是    | string | 登录 token |

#### Path 参数

| 参数名 | 是否必填 | 类型    | 说明    |
| --- | ---- | ----- | ----- |
| id  | 是    | int64 | 评价 ID |

#### 返回结果

统一 `Result`

#### 前端对接说明

1. 只应对“我自己的评价”显示删除按钮
2. 删除前建议二次确认
3. 删除成功后刷新当前页列表

---

# 3. Schemas（按对接最常用的模型补充说明）

虽然上面已经在每个 API 中解释了主要字段，但这里保留一个简化模型索引，方便查阅。

## 3.1 UserDTO

| 字段       | 类型     | 说明    |
| -------- | ------ | ----- |
| id       | number | 用户 ID |
| nickName | string | 昵称    |
| icon     | string | 头像    |

## 3.2 UserProfileDTO

| 字段        | 类型              | 说明    |
| --------- | --------------- | ----- |
| userId    | number          | 用户 ID |
| phone     | string          | 手机号   |
| password  | string          | 密码字段  |
| nickname  | string          | 昵称    |
| icon      | string          | 头像    |
| city      | string          | 城市    |
| introduce | string          | 简介    |
| gender    | boolean \| null | 性别    |
| birthday  | string \| null  | 生日    |
| credits   | number          | 积分    |

## 3.3 AdminUserDTO

| 字段        | 类型              | 说明           |
| --------- | --------------- | ------------ |
| userId    | number          | 用户 ID        |
| phone     | string          | 手机号          |
| password  | string          | 密码字段         |
| role      | number          | 0 用户 / 1 管理员 |
| nickname  | string          | 昵称           |
| icon      | string          | 头像           |
| city      | string          | 城市           |
| introduce | string          | 简介           |
| gender    | boolean \| null | 性别           |
| birthday  | string \| null  | 生日           |
| credits   | number \| null  | 积分           |

## 3.4 Canteen

| 字段         | 类型     | 说明    |
| ---------- | ------ | ----- |
| id         | number | 食堂 ID |
| name       | string | 名称    |
| address    | string | 地址    |
| images     | string | 图片    |
| x          | number | 坐标 X  |
| y          | number | 坐标 Y  |
| crowdLevel | number | 拥挤等级  |
| crowdScore | number | 拥挤分值  |
| openHours  | string | 营业时间  |
| status     | number | 状态    |

## 3.5 Window

| 字段            | 类型     | 说明    |
| ------------- | ------ | ----- |
| id            | number | 窗口 ID |
| canteenId     | number | 食堂 ID |
| name          | string | 名称    |
| description   | string | 描述    |
| waitTime      | number | 等待时长  |
| waitTimeLevel | number | 等待等级  |
| status        | number | 状态    |
| sort          | number | 排序值   |

## 3.6 Dish

| 字段          | 类型     | 说明     |
| ----------- | ------ | ------ |
| id          | number | 菜品 ID  |
| windowId    | number | 窗口 ID  |
| name        | string | 名称     |
| description | string | 描述     |
| image       | string | 图片     |
| price       | number | 价格     |
| quantity    | number | 库存     |
| tags        | string | 标签     |
| avgRating   | number | 平均评分   |
| ratingCount | number | 评分人数   |
| status      | number | 状态     |
| isToday     | number | 是否今日供应 |

## 3.7 FoodOrderSimpleDTO

| 字段          | 类型     | 说明    |
| ----------- | ------ | ----- |
| id          | number | 订单 ID |
| orderNo     | string | 订单编号  |
| userId      | number | 用户 ID |
| totalAmount | number | 总金额   |
| totalCount  | number | 总件数   |
| status      | number | 订单状态  |
| pickupCode  | string | 取餐码   |
| createTime  | string | 创建时间  |
| updateTime  | string | 更新时间  |

## 3.8 FoodOrderDetailDTO

在 `FoodOrderSimpleDTO` 基础上增加：

| 字段           | 类型     | 说明    |
| ------------ | ------ | ----- |
| remark       | string | 备注    |
| cancelReason | string | 取消原因  |
| items        | array  | 订单项列表 |

## 3.9 DishReview

| 字段      | 类型     | 说明    |
| ------- | ------ | ----- |
| id      | number | 评价 ID |
| dishId  | number | 菜品 ID |
| userId  | number | 用户 ID |
| rating  | number | 评分    |
| content | string | 内容    |
| tags    | string | 标签    |
| images  | string | 图片    |

---
