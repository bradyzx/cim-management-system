# Vben Admin 5 Mock 接口清单

> **来源**：`apps/backend-mock` 源码逐文件核对，非官方文档转述。
> **仓库**：vue-vben-admin，提交 `0d37806`（2026-11-01）
> **用途**：作为 Java（或任何语言）后端实现接口的对照清单 / PR SPEC 的接口范围依据。

---

## 一、通用约定

### 1.1 基址与前缀

| 项 | 值 |
|---|---|
| 开发环境 baseURL | `/api`（`VITE_GLOB_API_URL`） |
| 路由规则 | 后端所有接口挂在 `/api/**` 下 |
| 跨域 | 已开启 CORS，允许携带凭证（`Access-Control-Allow-Credentials: true`） |
| Mock 开关 | `VITE_NITRO_MOCK=true` 时走本地 Nitro 服务，接真实后端需置为 `false` |

前端源码 `apps/web-antd/src/api/core/*.ts` 中写的路径**不含** `/api` 前缀（如 `'/auth/login'`），由 baseURL 拼接。

### 1.2 鉴权

| 项 | 值 |
|---|---|
| 请求头 | `Authorization: Bearer <accessToken>` |
| accessToken 有效期 | 7 天（JWT，密钥 `access_token_secret`） |
| refreshToken 载体 | HttpOnly Cookie，Cookie 名 **`jwt`** |
| Cookie 属性 | `httpOnly=true, sameSite=none, secure=true, maxAge=24h` |
| refreshToken 有效期 | 30 天（JWT，密钥 `refresh_token_secret`） |

注意 accessToken 7 天 / refreshToken 30 天 / Cookie 只存 24 小时，三者不一致，属于 mock 的简化，不能直接照搬到生产。

### 1.3 统一响应结构

成功：

```json
{ "code": 0, "data": {}, "error": null, "message": "ok" }
```

失败：

```json
{ "code": -1, "data": null, "error": "...", "message": "错误描述" }
```

- 判定成功以 `code === 0` 为准，不看 HTTP 状态码。
- 401 / 403 场景额外设置 HTTP 状态码，但 body 仍是 `code: -1`。

### 1.4 分页响应结构

```json
{
  "code": 0,
  "data": { "items": [], "total": 100 },
  "error": null,
  "message": "ok"
}
```

| 参数 | 说明 |
|---|---|
| `page` | 页码，**从 1 开始** |
| `pageSize` | 每页条数，默认 20（部分接口 10） |
| `items` | 当前页数据数组（**不是** `records` / `list`） |
| `total` | 过滤后的总条数（不是当前页条数） |

---

## 二、接口清单

### 2.1 认证模块（Auth）

| 方法 | 路径 | 鉴权 | 入参 | 返回 `data` |
|---|---|---|---|---|
| POST | `/auth/login` | 否 | body: `{username, password}` | `{...userInfo, accessToken}`，同时 Set-Cookie `jwt` |
| POST | `/auth/refresh` | Cookie | 无 body，带 `withCredentials` | **裸字符串** accessToken |
| POST | `/auth/logout` | Cookie | 无 | `""` 空字符串 |
| GET | `/auth/codes` | 是 | 无 | `string[]` 权限码，如 `["AC_100010"]` |

**实现要点**：

- `/auth/refresh` 返回的 `data` 是字符串本身，不是 `{accessToken: "..."}` 对象。Java 端 `R<String>` 直接塞 token 即可，多加一层对象前端会解析失败。
- 登录失败返回 HTTP 403 + `code:-1`，文案 `Username or password is incorrect.`
- 用户名密码缺失返回 HTTP 400。

### 2.2 用户与菜单

| 方法 | 路径 | 鉴权 | 返回 `data` |
|---|---|---|---|
| GET | `/user/info` | 是 | `UserInfo` 对象（不含 password） |
| GET | `/menu/all` | 是 | 当前用户的动态路由树 |

**`UserInfo` 结构**：

```ts
{
  id: number;
  realName: string;
  roles: string[];      // 如 ["super"] / ["admin"] / ["user"]
  username: string;
  homePath?: string;    // 登录后落地页，如 "/dashboard/workspace"
}
```

**菜单节点结构**（`/menu/all` 返回树，按 `children` 嵌套）：

```ts
{
  name: string;              // 路由名，全局唯一
  path: string;              // 绝对路径或相对父级路径
  component?: string;        // 组件路径，如 "/dashboard/analytics/index"
  redirect?: string;
  meta: {
    title: string;           // i18n key
    icon?: string;           // 图标名
    order?: number;          // 排序
    keepAlive?: boolean;
    affixTab?: boolean;
    authority?: string[];    // 可见所需角色
    menuVisibleWithForbidden?: boolean
  };
  children?: 菜单节点[]
}
```

菜单是**由后端按角色动态下发**的，前端不写死路由。这是接 Java 后端时最核心的一个接口——菜单树结构错了，整个侧边栏和权限都会失效。

### 2.3 字典（运行时读取）

| 方法 | 路径 | 鉴权 | 入参 | 返回 `data` |
|---|---|---|---|---|
| GET | `/dict/all` | 是 | 无 | `{ [dictCode]: [{label, value, color?}] }` |
| GET | `/dict/data` | 是 | query: `code` | `[{label, value, color?}]` |
| GET | `/dict/version` | 是 | 无 | `number` 版本号 |

**实现要点**：

- `/dict/all` 一次性返回全部启用字典，按 dictCode 分组，前端做本地缓存。
- `/dict/version` 是关键设计：字典增删改后版本号 +1，前端轮询比对版本号决定是否重新拉取 `/dict/all`。Java 端建议用一个 Redis key 或数据库版本字段实现，不要省略。
- `value` 允许数字或字符串；`color` 可选，用于标签着色（如 `success` / `error`）。

### 2.4 系统管理 - 查询

| 方法 | 路径 | 分页 | 查询参数 |
|---|---|---|---|
| GET | `/system/user/list` | 是 | `page, pageSize, name, id, remark, startTime, endTime, deptId, gender, status` |
| GET | `/system/role/list` | 是 | `page, pageSize, name, id, remark, startTime, endTime, status` |
| GET | `/system/menu/list` | **否**，返回完整树 | 无 |
| GET | `/system/dept/list` | **否**，返回完整树 | 无 |
| GET | `/system/dict/type/list` | 是 | `page, pageSize, name, code, status` |
| GET | `/system/dict/data/list` | 是 | `page, pageSize, dictCode`（**必填**）, `label, status` |

`/system/dict/data/list` 不传 `dictCode` 时返回 `code:-1`，message 为 `dictCode is required`。

### 2.5 系统管理 - 唯一性校验

| 方法 | 路径 | 入参 | 返回 `data` |
|---|---|---|---|
| GET | `/system/dict/type/code-exists` | `code, id?` | `boolean`，true 表示已存在 |
| GET | `/system/menu/name-exists` | `name, id?` | `boolean` |
| GET | `/system/menu/path-exists` | `path, id?` | `boolean` |

语义是「是否已存在」，带 `id` 时排除自身（编辑场景）。

### 2.6 系统管理 - 写操作

| 方法 | 路径 | 入参 | 说明 |
|---|---|---|---|
| POST | `/system/dict/type` | `{code, name, remark?, status?}` | 真实写入内存 |
| PUT | `/system/dict/type/:id` | 同上，均可选 | 真实写入，改 code 会级联更新字典项 |
| DELETE | `/system/dict/type/:id` | — | 真实删除，级联删除其下字典项 |
| POST | `/system/dict/data` | `{dictCode, label, value, sort?, status?, color?, remark?}` | 真实写入 |
| PUT | `/system/dict/data/:id` | 同上，均可选 | 真实写入 |
| DELETE | `/system/dict/data/:id` | — | 真实删除 |
| POST | `/system/dept` | — | **空壳**，sleep 600ms 返回 `null` |
| PUT | `/system/dept/:id` | — | **空壳**，返回 `null` |
| DELETE | `/system/dept/:id` | — | **空壳**，sleep 1s 返回 `null` |

### 2.7 辅助接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/table/list` | 是 | 演示表格分页，额外支持 `sortBy` + `sortOrder`（`asc`/`desc`） |
| POST | `/upload` | 是 | 返回 `{url}`，当前指向 unpkg 上的示例图 |
| GET | `/status` | 否 | 按 query 的 `status` 返回对应 HTTP 状态码，用于调错误页 |
| GET | `/timezone` | 是 | 当前时区字符串 |
| GET | `/timezone/options` | **否** | `[{label, value}]` 时区下拉选项 |
| POST | `/timezone` | 是 | body `{timezone}`，非白名单值返回 400 |
| GET | `/demo/bigint` | 是 | 返回超出 JS 安全整数范围的 ID，测试精度丢失问题 |

---

## 三、接 Java 后端时最容易踩的坑

### 3.1 演示中间件会拦掉大半写接口

`middleware/1.api.ts` 里有一条硬规则：

```
POST / PUT / PATCH / DELETE 且 path 以 /api/system/ 开头
且 path 不以 /api/system/dict/ 开头  →  直接 403 "演示环境，禁止修改"
```

**后果**：`/system/dept` 的 POST/PUT/DELETE 虽然有对应文件，但请求在中间件层就被 403 拦掉了，永远执行不到（文件里还随机 sleep 0–2 秒模拟延迟）。

**结论**：这份 mock 里**只有字典（dict）是真正可写、可验证的 CRUD**。菜单、角色、用户、部门都只有查询，没有可用的写接口。你的 Java 后端不要照着 dept 的写接口去做参考，它没有实现。

### 3.2 `/auth/refresh` 返回裸字符串

见 2.1。这是最容易写错的一处。

### 3.3 Cookie 的 secure + sameSite=none 在本地 http 下不生效

浏览器要求 `secure=true` 的 Cookie 必须走 HTTPS，`sameSite=none` 也必须配 `secure`。本地开发用 `http://localhost` 时，这个 Cookie 可能写不进去，导致刷新 token 失败。

Java 端本地环境建议临时改成 `sameSite=lax` + `secure=false`，生产再改回来。

### 3.4 分页字段名是 `items`

不是 MyBatis-Plus 默认的 `records`，也不是 `list`。用 MP 的 `Page<T>` 直接返回会多出一层，需要包一层 VO：

```java
// 前端期望：{ code, data: { items: [...], total }, error, message }
```

### 3.5 错误用业务码，不是 HTTP 状态码

除了 401/403，绝大多数业务错误（参数缺失、重复、未找到）都返回 **HTTP 200 + `code:-1`**。如果 Java 端习惯用 `@ExceptionHandler` 统一返回 500 或 400，前端会走不同的错误分支。

### 3.6 菜单树是权限的核心，不是附属功能

`/menu/all` 返回的树直接决定：
- 侧边栏渲染哪些项
- 前端注册哪些路由
- `meta.authority` 控制角色可见性
- `type: 'button'` 的节点配合 `authCode` 控制按钮级权限

Java 端需要一张能表达「目录 / 菜单 / 按钮 / 内嵌 / 外链」五种类型的菜单表，以及角色-菜单关联。这是整套后端里工作量最大、也最不能省的部分。

---

## 四、建议的实现顺序

如果按这份清单写 Java 后端，建议顺序：

1. **统一响应包装**（`R<T>` + 全局异常处理）—— 所有接口的地基
2. **认证四件套**：login / refresh / logout / codes —— 前端能登进来
3. **`/user/info` + `/menu/all`** —— 前端能渲染出完整界面和菜单
4. **字典三件套**：all / data / version —— 页面上的下拉框、状态标签能显示
5. **业务模块 CRUD** —— 按实际业务逐个补，`/table/list` 可作为分页模板

前四步做完，前端就能以「真实后端模式」完整跑起来，这比先做业务功能更划算。

---

## 五、本文档的核对范围

已逐行阅读的源文件：

```
utils/response.ts          utils/jwt-utils.ts        utils/cookie-utils.ts
utils/mock-data.ts         utils/dict-utils.ts       middleware/1.api.ts
nitro.config.ts            api/auth/*.ts             api/user/info.ts
api/menu/all.ts            api/dict/*.ts             api/upload.ts
api/status.ts              api/table/list.ts         api/timezone/*.ts
api/demo/bigint.ts         api/system/**/*.ts
```

未覆盖：`api/test.get.ts`、`api/test.post.ts`（框架健康检查用，无业务含义）、`routes/[...].ts`（404 兜底）、`utils/timezone-utils.ts`（内部实现，无契约意义）。
