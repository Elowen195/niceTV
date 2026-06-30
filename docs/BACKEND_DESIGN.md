# NiceTV Backend Design

## 目标

为 NiceTV 增加账号系统、云端收藏同步和评论能力。后端只保存账号、用户生成内容和同步数据，不托管视频文件，不代理真实播放地址，也不保存观看历史。

MVP 先保证功能闭环：

- 注册、登录、刷新 token、退出登录。
- 登录用户收藏云同步，支持多设备增量合并。
- 视频详情页评论、回复、点赞、删除自己的评论。
- 可在 1c2g VPS 上用 Docker Compose 运行。

## 技术选型

- Language: Go 1.22。
- Router: Chi。
- Database: PostgreSQL 16。
- Auth: BCrypt + JWT access token + refresh token hash。
- Deploy: Docker / Docker Compose。
- Cache: MVP 不引 Redis。限流、热点缓存、验证码等需要用户量上来后再加。

选择 Go 的原因是部署包小、资源占用低、简历讲述直接：从 Android 客户端扩展到独立后端服务，重点落在接口设计、认证、数据同步和部署。

## 服务边界

后端负责：

- 用户身份和资料。
- refresh token 会话。
- `video_refs` 页面引用。
- 收藏快照与同步状态。
- 评论、回复、点赞计数。

后端不负责：

- 视频搜索、解析、下载、转码。
- 视频文件存储或播放链路代理。
- 未经用户确认的观看历史同步。

## 核心模型

```text
users
- id uuid pk
- username varchar unique
- email varchar unique nullable
- password_hash text
- avatar_url text nullable
- bio text nullable
- role text
- created_at timestamptz
- updated_at timestamptz

refresh_tokens
- id uuid pk
- user_id uuid fk
- token_hash text unique
- device_name text nullable
- expires_at timestamptz
- revoked_at timestamptz nullable
- created_at timestamptz

video_refs
- id uuid pk
- source text
- source_url text unique
- title text
- cover_url text nullable
- maker text nullable
- created_at timestamptz
- updated_at timestamptz

favorites
- id uuid pk
- user_id uuid fk
- video_ref_id uuid fk
- title_snapshot text
- cover_snapshot text nullable
- maker_snapshot text nullable
- tags_snapshot jsonb
- created_at timestamptz
- updated_at timestamptz
- deleted_at timestamptz nullable
- unique(user_id, video_ref_id)

comments
- id uuid pk
- user_id uuid fk
- video_ref_id uuid fk
- parent_id uuid nullable
- body text
- status text
- like_count int
- created_at timestamptz
- updated_at timestamptz
- deleted_at timestamptz nullable

comment_reactions
- user_id uuid fk
- comment_id uuid fk
- reaction text
- created_at timestamptz
- primary key(user_id, comment_id)
```

## API

所有接口使用 JSON。需要登录的接口使用：

```http
Authorization: Bearer <accessToken>
```

### Auth

```http
POST /v1/auth/register
POST /v1/auth/login
POST /v1/auth/refresh
POST /v1/auth/logout
GET  /v1/me
PATCH /v1/me
```

登录和注册返回 access token、refresh token 和用户信息。Access token 默认 15 分钟过期，refresh token 默认 30 天过期。刷新 token 时服务端会撤销旧 refresh token 并签发新 refresh token。

### Video Ref

客户端进入详情页、收藏或评论前，先用页面 URL upsert 一个 `video_ref`。

```http
POST /v1/video-refs
```

```json
{
  "source": "supjav",
  "sourceUrl": "https://supjav.com/438815.html",
  "title": "title snapshot",
  "coverUrl": "https://...",
  "maker": "maker"
}
```

### Favorites

```http
GET    /v1/favorites?limit=30
PUT    /v1/favorites/{videoRefId}
DELETE /v1/favorites/{videoRefId}
POST   /v1/favorites/sync
```

收藏同步使用 Last Write Wins。客户端上传本地变更时带 `updatedAt`，服务端保留 `deleted_at` tombstone，避免多设备离线后误恢复已删除收藏。

```json
{
  "since": "2026-06-30T00:00:00Z",
  "changes": [
    {
      "source": "supjav",
      "sourceUrl": "https://supjav.com/438815.html",
      "op": "upsert",
      "updatedAt": "2026-06-30T00:01:00Z",
      "snapshot": {
        "title": "title snapshot",
        "coverUrl": "https://...",
        "maker": "maker",
        "tags": ["favorite"]
      }
    }
  ]
}
```

服务端返回：

```json
{
  "serverTime": "2026-06-30T00:02:00Z",
  "changes": []
}
```

### Comments

```http
GET    /v1/video-refs/{videoRefId}/comments?cursor=...&limit=30
POST   /v1/video-refs/{videoRefId}/comments
PATCH  /v1/comments/{commentId}
DELETE /v1/comments/{commentId}
PUT    /v1/comments/{commentId}/like
DELETE /v1/comments/{commentId}/like
```

评论正文限制 1 到 1000 字。删除评论为软删除，列表只返回 `visible` 评论。`parent_id` 已预留回复能力。

## Android 集成顺序

1. 先接登录和 token 刷新。
2. 详情页打开时调用 `POST /v1/video-refs`，拿到 `videoRefId`。
3. 登录后触发收藏同步：本地收藏上行，云端增量下行。
4. 收藏表增加 `remoteId / syncState / updatedAt / deletedAt`。
5. 评论区放在详情页播放器下方，首屏只拉 20 到 30 条。
6. 网络失败时把收藏和评论写操作放进本地队列，后台重试。

## 安全与后续

MVP 已做：

- 密码 BCrypt 哈希。
- Refresh token 只存 SHA-256 hash。
- 写接口要求 JWT。
- 评论和资料字段做长度限制。
- 数据库唯一约束和外键约束。

后续再做：

- Redis 或内存限流。
- 验证码 / 邮箱验证。
- 举报、审核队列、管理后台。
- refresh token 设备管理。
- tombstone 定期清理任务。

## 简历表述

- 设计并实现 NiceTV 账号系统、评论系统和云端收藏同步服务，基于 Go、JWT、PostgreSQL 构建用户数据闭环。
- 设计离线优先的收藏同步协议，支持多设备增量同步、软删除 tombstone 和 Last Write Wins 冲突合并。
- 为评论系统设计回复、点赞聚合、软删除和审核状态模型，为后续管理后台预留扩展点。
