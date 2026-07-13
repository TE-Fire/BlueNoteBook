# BlueNote 微服务 API 接口文档

## 概述

本文档描述了 BlueNote 小蓝书微服务架构项目的 RESTful API 接口。所有接口统一通过网关层访问，网关端口配置来自 `bluenote-gateway/application.yml`。

## 基础信息

| 项目 | 说明 |
|------|------|
| 服务名称 | BlueNote Gateway |
| 基础路径 | `/` |
| 网关端口 | `8000` |
| 环境 | Development |
| 网关配置来源 | `bluenote-gateway/src/main/resources/application.yml` |

---

## 接口总览

| 模块 | 前缀 | 服务 | 接口数量 |
|------|------|------|----------|
| 用户认证 | `/auth` | bluenote-auth | 4 |
| 验证码服务 | `/auth/verification` | bluenote-auth | 1 |
| 用户服务 | `/user` | bluenote-user | 6 |
| 文件服务 | `/file` | bluenote-oss | 1 |
| 笔记服务 | `/note` | bluenote-note | 6 |
| 用户关系 | `/relation` | bluenote-user-relation | 4 |

---

## 一、用户认证模块

### 1.1 用户登录/注册

**接口地址**: `POST http://localhost:8000/auth/login`

**功能描述**: 用户登录或注册接口，支持手机号验证码登录和账号密码登录两种方式

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phone | String | 是 | 手机号 |
| code | String | 否 | 验证码（验证码登录时必填） |
| password | String | 否 | 密码（密码登录时必填） |
| type | Integer | 是 | 登录类型：1-手机号验证码登录，2-账号密码登录 |

**请求示例（验证码登录）**:

```json
{
    "phone": "13800138000",
    "code": "123456",
    "password": null,
    "type": 1
}
```

**请求示例（密码登录）**:

```json
{
    "phone": "13800138000",
    "code": null,
    "password": "123456",
    "type": 2
}
```

**成功响应**:

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**失败响应**:

```json
{
    "success": false,
    "message": "验证码错误",
    "errorCode": "AUTH-20001",
    "data": null
}
```

---

### 1.2 用户登出

**接口地址**: `POST http://localhost:8000/auth/logout`

**功能描述**: 用户登出接口，无需鉴权

**成功响应**:

```json
{
    "success": true,
    "message": "登出成功",
    "errorCode": null,
    "data": null
}
```

**失败响应**:

```json
{
    "success": false,
    "message": "用户未登录",
    "errorCode": "AUTH-30001",
    "data": null
}
```

---

### 1.3 修改密码（认证模块）

**接口地址**: `POST http://localhost:8000/auth/password/update`

**功能描述**: 用户修改密码接口

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| newPassword | String | 是 | 新密码 |

**请求示例**:

```json
{
    "newPassword": "123456"
}
```

**成功响应**:

```json
{
    "success": true,
    "message": "密码修改成功",
    "errorCode": null,
    "data": null
}
```

**失败响应**:

```json
{
    "success": false,
    "message": "新密码不能为空",
    "errorCode": "AUTH-10001",
    "data": null
}
```

---

## 二、验证码服务

### 2.1 发送短信验证码

**接口地址**: `POST http://localhost:8000/auth/verification/code/send`

**功能描述**: 发送短信验证码到指定手机号

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phone | String | 是 | 手机号 |

**请求示例**:

```json
{
    "phone": "13800138000"
}
```

**成功响应**:

```json
{
    "success": true,
    "message": "短信发送成功",
    "errorCode": null,
    "data": null
}
```

**失败响应**:

```json
{
    "success": false,
    "message": "发送短信太频繁，请稍后再试",
    "errorCode": "AUTH-20002",
    "data": null
}
```

---

## 三、用户服务模块

### 3.1 用户信息修改

**接口地址**: `POST http://localhost:8000/user/user/update`

**功能描述**: 用户信息修改接口，支持昵称、头像、小蓝书号、性别、生日、个人简介、背景图

**请求体** (multipart/form-data):

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| nickname | String | 否 | 昵称 |
| avatar | File | 否 | 头像文件 |
| xiaohashuId | String | 否 | 小蓝书号 |
| sex | Integer | 否 | 性别 |
| birthday | String | 否 | 生日 (yyyy-MM-dd) |
| introduction | String | 否 | 个人简介 |
| backgroundImg | File | 否 | 背景图 |

**成功响应**:

```json
{
    "success": true,
    "message": "更新成功",
    "errorCode": null,
    "data": null
}
```

---

### 3.2 用户注册

**接口地址**: `POST http://localhost:8000/user/user/register`

**功能描述**: 用户注册接口

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phone | String | 是 | 手机号 |

**请求示例**:

```json
{
    "phone": "13800138000"
}
```

**成功响应**:

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": 1
}
```

---

### 3.3 手机号查询用户信息

**接口地址**: `POST http://localhost:8000/user/user/findByPhone`

**功能描述**: 根据手机号查询用户信息

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phone | String | 是 | 手机号 |

**请求示例**:

```json
{
    "phone": "13800138000"
}
```

**成功响应**:

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": {
        "id": 1,
        "password": "encrypted_password"
    }
}
```

---

### 3.4 密码更新（用户模块）

**接口地址**: `POST http://localhost:8000/user/user/password/update`

**功能描述**: 用户密码更新接口

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| encodePassword | String | 是 | 加密后的密码 |

**请求示例**:

```json
{
    "encodePassword": "encrypted_password"
}
```

**成功响应**:

```json
{
    "success": true,
    "message": "密码更新成功",
    "errorCode": null,
    "data": null
}
```

---

### 3.5 查询用户信息

**接口地址**: `POST http://localhost:8000/user/user/findById`

**功能描述**: 根据用户ID查询用户信息

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 用户ID |

**请求示例**:

```json
{
    "id": 1
}
```

**成功响应**:

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": {
        "id": 1,
        "nickName": "test",
        "avatar": "https://example.com/avatar.jpg",
        "introduction": "个人简介"
    }
}
```

---

### 3.6 批量查询用户信息

**接口地址**: `POST http://localhost:8000/user/user/findByIds`

**功能描述**: 批量根据用户ID查询用户信息

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ids | Long[] | 是 | 用户ID列表 |

**请求示例**:

```json
{
    "ids": [1, 2, 3]
}
```

**成功响应**:

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": [
        {
            "id": 1,
            "nickName": "test1",
            "avatar": "https://example.com/avatar1.jpg",
            "introduction": "个人简介1"
        },
        {
            "id": 2,
            "nickName": "test2",
            "avatar": "https://example.com/avatar2.jpg",
            "introduction": "个人简介2"
        }
    ]
}
```

---

## 四、文件服务模块

### 4.1 上传文件

**接口地址**: `POST http://localhost:8000/file/file/upload`

**功能描述**: 文件上传接口，支持上传图片、文档等文件，无需鉴权

**请求体** (multipart/form-data):

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | 上传的文件 |

**成功响应**:

```json
{
    "success": true,
    "message": "文件上传成功",
    "errorCode": null,
    "data": {
        "url": "https://oss.example.com/2026/06/25/test.jpg",
        "fileName": "test.jpg",
        "fileSize": 102400
    }
}
```

**失败响应**:

```json
{
    "success": false,
    "message": "文件不能为空",
    "errorCode": "OSS-10001",
    "data": null
}
```

---

## 五、笔记服务模块

### 5.1 笔记发布

**接口地址**: `POST http://localhost:8000/note/note/publish`

**功能描述**: 发布新笔记

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| content | String | 是 | 笔记内容 |
| title | String | 否 | 笔记标题 |
| images | String[] | 否 | 图片URL列表 |
| topicIds | Long[] | 否 | 话题ID列表 |

**请求示例**:

```json
{
    "content": "笔记内容",
    "title": "笔记标题",
    "images": ["https://example.com/img1.jpg"],
    "topicIds": [1]
}
```

**成功响应**:

```json
{
    "success": true,
    "message": "发布成功",
    "errorCode": null,
    "data": null
}
```

---

### 5.2 笔记详情

**接口地址**: `POST http://localhost:8000/note/note/detail`

**功能描述**: 查询笔记详情

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| noteId | Long | 是 | 笔记ID |

**请求示例**:

```json
{
    "noteId": 1
}
```

**成功响应**:

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": {
        "noteId": 1,
        "content": "笔记内容",
        "title": "笔记标题",
        "images": ["https://example.com/img1.jpg"]
    }
}
```

---

### 5.3 笔记修改

**接口地址**: `POST http://localhost:8000/note/note/update`

**功能描述**: 修改笔记内容

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| noteId | Long | 是 | 笔记ID |
| content | String | 否 | 笔记内容 |
| title | String | 否 | 笔记标题 |

**请求示例**:

```json
{
    "noteId": 1,
    "content": "修改后的内容",
    "title": "修改后的标题"
}
```

**成功响应**:

```json
{
    "success": true,
    "message": "修改成功",
    "errorCode": null,
    "data": null
}
```

---

### 5.4 删除笔记

**接口地址**: `POST http://localhost:8000/note/note/delete`

**功能描述**: 删除笔记

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| noteId | Long | 是 | 笔记ID |

**请求示例**:

```json
{
    "noteId": 1
}
```

**成功响应**:

```json
{
    "success": true,
    "message": "删除成功",
    "errorCode": null,
    "data": null
}
```

---

### 5.5 笔记仅对自己可见

**接口地址**: `POST http://localhost:8000/note/note/visible/onlyme`

**功能描述**: 设置笔记仅对自己可见

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| noteId | Long | 是 | 笔记ID |
| visibleOnlyMe | Boolean | 是 | 是否仅自己可见 |

**请求示例**:

```json
{
    "noteId": 1,
    "visibleOnlyMe": true
}
```

**成功响应**:

```json
{
    "success": true,
    "message": "设置成功",
    "errorCode": null,
    "data": null
}
```

---

### 5.6 置顶/取消置顶笔记

**接口地址**: `POST http://localhost:8000/note/note/top`

**功能描述**: 置顶或取消置顶笔记

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| noteId | Long | 是 | 笔记ID |
| isTop | Boolean | 是 | 是否置顶 |

**请求示例**:

```json
{
    "noteId": 1,
    "isTop": true
}
```

**成功响应**:

```json
{
    "success": true,
    "message": "设置成功",
    "errorCode": null,
    "data": null
}
```

---

## 六、用户关系模块

### 6.1 关注用户

**接口地址**: `POST http://localhost:8000/relation/relation/follow`

**功能描述**: 关注指定用户

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| followUserId | Long | 是 | 被关注用户ID |

**请求示例**:

```json
{
    "followUserId": 2
}
```

**成功响应**:

```json
{
    "success": true,
    "message": "关注成功",
    "errorCode": null,
    "data": null
}
```

---

### 6.2 取关用户

**接口地址**: `POST http://localhost:8000/relation/relation/unfollow`

**功能描述**: 取消关注指定用户

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| unfollowUserId | Long | 是 | 被取关用户ID |

**请求示例**:

```json
{
    "unfollowUserId": 2
}
```

**成功响应**:

```json
{
    "success": true,
    "message": "取关成功",
    "errorCode": null,
    "data": null
}
```

---

### 6.3 查询用户关注列表

**接口地址**: `POST http://localhost:8000/relation/relation/following/list`

**功能描述**: 分页查询用户关注列表

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | Long | 是 | 用户ID |
| pageNo | Integer | 是 | 页码 |

**请求示例**:

```json
{
    "userId": 1,
    "pageNo": 1
}
```

**成功响应**:

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": [
        {
            "userId": 2,
            "avatar": "https://example.com/avatar2.jpg",
            "nickname": "user2",
            "introduction": "简介"
        }
    ],
    "pageNo": 1,
    "totalCount": 10,
    "pageSize": 10,
    "totalPage": 1
}
```

---

### 6.4 查询用户粉丝列表

**接口地址**: `POST http://localhost:8000/relation/relation/fans/list`

**功能描述**: 分页查询用户粉丝列表

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | Long | 是 | 用户ID |
| pageNo | Integer | 是 | 页码 |

**请求示例**:

```json
{
    "userId": 1,
    "pageNo": 1
}
```

**成功响应**:

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": [
        {
            "userId": 3,
            "avatar": "https://example.com/avatar3.jpg",
            "nickname": "fan1",
            "introduction": "粉丝简介"
        }
    ],
    "pageNo": 1,
    "totalCount": 5,
    "pageSize": 10,
    "totalPage": 1
}
```

---

## 七、错误码说明

| 错误码 | 说明 |
|--------|------|
| AUTH-10000 | 系统错误 |
| AUTH-10001 | 参数错误 |
| AUTH-20001 | 验证码错误 |
| AUTH-20002 | 发送短信太频繁 |
| AUTH-30001 | 用户未登录 |
| OSS-10001 | 文件上传错误 |

---

## 八、通用响应格式

```json
{
    "success": true,
    "message": "响应消息",
    "errorCode": "错误码",
    "data": {}
}
```

| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 接口调用是否成功 |
| message | String | 响应消息，成功时可为 null |
| errorCode | String | 错误码，成功时可为 null |
| data | Object | 响应数据，根据接口不同而变化 |

---

## 九、认证方式

- **Token 认证**: 登录成功后返回的 Token 需要放在请求头 `Authorization: Bearer <token>` 中
- **无认证接口**: 以下接口无需认证：
  - `/auth/login` - 用户登录
  - `/auth/logout` - 用户登出
  - `/auth/verification/code/send` - 发送验证码
  - `/file/file/upload` - 文件上传

---

## 十、网关路由规则

所有请求统一通过网关 `http://localhost:8000` 访问，网关路由规则如下：

| 路由ID | 路径前缀 | 目标服务 | StripPrefix |
|--------|----------|----------|-------------|
| auth | `/auth/**` | bluenote-auth | 1 |
| user | `/user/**` | bluenote-user | 1 |
| note | `/note/**` | bluenote-note | 1 |
| user-relation | `/relation/**` | bluenote-user-relation | 1 |
| oss | `/file/**` | bluenote-oss | 1 |

> **说明**: `StripPrefix=1` 表示移除路径的第一个前缀，例如 `/auth/login` 请求会转发到 `bluenote-auth` 服务的 `/login` 接口