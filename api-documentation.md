# BlueNote 认证服务接口文档

## 概述

本文档描述了 BlueNote 认证服务（Auth Module）的 RESTful API 接口。

## 基础信息

| 项目 | 说明 |
|------|------|
| 服务名称 | BlueNote Auth Service |
| 基础路径 | `/` |
| 服务端口 | `8080` |
| 环境 | Development |

---

## 接口列表

### 1. 用户信息修改

**接口地址**: `POST /user/update`

**功能描述**: 用户信息修改接口

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| nickname | String | 否 | 昵称 |
| avatar | File | 否 | 头像文件（multipart/form-data） |

**成功响应**:

| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 响应消息 |
| errorCode | String | 异常码 |
| data | Object | 响应数据 |

---

### 2. 上传文件

**接口地址**: `POST /file/upload`

**功能描述**: 文件上传接口，支持上传图片、文档等文件

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | 上传的文件（multipart/form-data） |

**成功响应**:

| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 响应消息 |
| errorCode | String | 异常码 |
| data | Object | 响应数据 |

**成功响应示例**:

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

**失败响应示例**:

```json
{
    "success": false,
    "message": "文件不能为空",
    "errorCode": "OSS-10001",
    "data": null
}
```

---

### 2. 修改密码

**接口地址**: `POST /user/password/update`

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

| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 响应消息 |
| errorCode | String | 异常码 |
| data | Object | 响应数据 |

**成功响应示例**:

```json
{
    "success": true,
    "message": "密码修改成功",
    "errorCode": null,
    "data": null
}
```

**失败响应示例**:

```json
{
    "success": false,
    "message": "新密码不能为空",
    "errorCode": "AUTH-10001",
    "data": null
}
```

---

### 2. 用户登出

**接口地址**: `POST /user/logout`

**功能描述**: 用户登出接口

**成功响应**:

| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 响应消息 |
| errorCode | String | 异常码 |
| data | Object | 响应数据 |

**成功响应示例**:

```json
{
    "success": true,
    "message": "登出成功",
    "errorCode": null,
    "data": null
}
```

**失败响应示例**:

```json
{
    "success": false,
    "message": "用户未登录",
    "errorCode": "AUTH-30001",
    "data": null
}
```

---

### 2. 用户登录/注册

**接口地址**: `POST /user/login`

**功能描述**: 用户登录或注册接口，支持手机号验证码登录和账号密码登录两种方式

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phone | String | 是 | 手机号 |
| code | String | 否 | 验证码（验证码登录时必填） |
| password | String | 否 | 密码（密码登录时必填） |
| type | Integer | 是 | 登录类型：1-手机号验证码登录，2-账号密码登录 |

**请求示例**:

```json
{
    "phone": "13800138000",
    "code": "123456",
    "password": null,
    "type": 1
}
```

**成功响应**:

| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 响应消息 |
| errorCode | String | 异常码 |
| data | String | 用户 Token |

**成功响应示例**:

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**失败响应示例**:

```json
{
    "success": false,
    "message": "验证码错误",
    "errorCode": "AUTH-20001",
    "data": null
}
```

---

### 2. 发送短信验证码

**接口地址**: `POST /verification/code/send`

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

| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 响应消息 |
| errorCode | String | 异常码 |
| data | Object | 响应数据 |

**成功响应示例**:

```json
{
    "success": true,
    "message": "短信发送成功",
    "errorCode": null,
    "data": null
}
```

**失败响应示例**:

```json
{
    "success": false,
    "message": "发送短信太频繁，请稍后再试",
    "errorCode": "AUTH-20002",
    "data": null
}
```

---

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| AUTH-10000 | 系统错误 |
| AUTH-10001 | 参数错误 |
| AUTH-20001 | 验证码错误 |
| AUTH-20002 | 发送短信太频繁 |
| AUTH-30001 | 用户未登录 |

---

## 通用响应格式

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

## 认证方式

- **Token 认证**: 登录成功后返回的 Token 需要放在请求头 `Authorization: Bearer <token>` 中
- **无认证接口**: `/file/upload`、`/user/login`、`/user/logout`、`/user/password/update`、`/user/update` 和 `/verification/code/send` 无需认证