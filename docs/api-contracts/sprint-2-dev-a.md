# Sprint 2 Dev A API Contract

This contract covers the course/content database foundation, Admin category
management, and course-thumbnail uploads.

## Shared decisions

- All Sprint 2 IDs are UUIDs.
- API base path is `/api/v1`.
- Category management and thumbnail upload require role `ADMIN`.
- Category responses are flat objects with `parentId`; clients build a tree when needed.
- Categories support at most two levels.
- Course sections use table name `course_sections`.
- The database migration is `V5__course_catalog_content_foundation.sql`.

## Admin Categories

### List categories

```text
GET /api/v1/admin/categories?keyword=&active=&parentId=
```

Returns all matching categories sorted by `sortOrder`, then `name`.

### Create category

```text
POST /api/v1/admin/categories
```

```json
{
  "name": "Cloud Computing",
  "slug": "cloud-computing",
  "description": "Optional description",
  "parentId": null,
  "isActive": true,
  "sortOrder": 10
}
```

`slug`, `description`, `parentId`, `isActive`, and `sortOrder` are optional.
When omitted, slug is generated from name, isActive defaults to `true`, and sort
order defaults to `0`.

### Get, update, and delete category

```text
GET    /api/v1/admin/categories/{categoryId}
PATCH  /api/v1/admin/categories/{categoryId}
DELETE /api/v1/admin/categories/{categoryId}
```

PATCH accepts any subset of create fields. Send `"parentId": null` to move a
child category to the root. Changing name does not change slug unless a new
slug is supplied.

Category response:

```json
{
  "id": "8ad3d80b-5c62-4caf-a74c-903ee7b13170",
  "name": "Cloud Computing",
  "slug": "cloud-computing",
  "description": "Optional description",
  "parentId": null,
  "isActive": true,
  "sortOrder": 10,
  "createdAt": "2026-06-13T03:00:00Z",
  "updatedAt": "2026-06-13T03:00:00Z"
}
```

Category errors:

| Code | HTTP | Meaning |
|---|---:|---|
| `CATEGORY_NOT_FOUND` | 404 | Category or requested parent was not found |
| `CATEGORY_SLUG_CONFLICT` | 409 | Normalized slug already exists |
| `CATEGORY_IN_USE` | 409 | Category has children or any course references, including soft-deleted courses |
| `CATEGORY_HIERARCHY_INVALID` | 422 | Self-parent, third level, or invalid move |

## Course Thumbnail Upload

```text
POST /api/v1/admin/uploads/course-thumbnails
Content-Type: multipart/form-data
Part name: file
```

Allowed actual MIME types are JPEG, PNG, and WebP. Client-provided MIME type
and original filename are not trusted. Maximum file size defaults to 5 MB.

Response data:

```json
{
  "url": "https://project.supabase.co/storage/v1/object/public/course-thumbnails/2026/06/generated-id.webp",
  "objectPath": "2026/06/generated-id.webp",
  "fileName": "generated-id.webp",
  "contentType": "image/webp",
  "size": 12345
}
```

Upload errors:

| Code | HTTP | Meaning |
|---|---:|---|
| `INVALID_REQUEST` | 400 | Missing, empty, or unreadable file |
| `PAYLOAD_TOO_LARGE` | 413 | File exceeds configured maximum |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Actual file content is not JPEG, PNG, or WebP |
| `EXTERNAL_SERVICE_UNAVAILABLE` | 503 | Supabase Storage is unconfigured or unavailable |

## Handoff Notes

- Dev B should reuse `Category`, `Course`, `CourseSection`, and `Lesson` entities
  and repositories instead of redefining schema mappings.
- Dev B should store only thumbnail URLs produced by the configured public
  Supabase Storage base URL.
- Dev C public category queries should return active categories only; a child
  category is public only while its parent is active.
- Dev E receives flat category data and may build a category tree in the UI.
- Dev F stores the upload response `url` in the course form.

## Environment

```text
SUPABASE_URL
SUPABASE_SERVICE_ROLE_KEY
SUPABASE_COURSE_THUMBNAIL_BUCKET=course-thumbnails
APP_STORAGE_COURSE_THUMBNAIL_MAX_SIZE=5MB
APP_STORAGE_COURSE_THUMBNAIL_MAX_REQUEST_SIZE=6MB
APP_SEED_CATEGORIES_ENABLED=false
```
