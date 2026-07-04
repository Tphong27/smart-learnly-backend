# HLS transcoding with GitHub Actions and Cloudflare R2

This is the first migration stage away from local FFmpeg processing. It validates
the cloud transcoding path before the backend upload contract is changed.

## Data flow

```text
Private R2 raw object
  -> GitHub-hosted Ubuntu runner
  -> FFmpeg (AES-128 HLS)
  -> Private R2 HLS prefix
  -> optional signed callback to the backend
```

The workflow is manual-only at this stage. It does not run for pushes or pull
requests and therefore does not expose R2 credentials to untrusted repository
events.

## Required GitHub Actions configuration

Repository secrets:

```text
R2_ACCESS_KEY_ID
R2_SECRET_ACCESS_KEY
HLS_CALLBACK_SECRET
```

Repository variables:

```text
R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
R2_REGION=auto
R2_RAW_BUCKET=<private-source-bucket>
R2_HLS_BUCKET=<private-hls-bucket>
```

`R2_RAW_BUCKET` and `R2_HLS_BUCKET` may have the same value when one private
bucket is separated by `raw/` and `hls/` prefixes.

The R2 token must have Object Read & Write permission scoped only to the
configured bucket or buckets. Do not configure an `r2.dev` public URL for the
protected video bucket.

The optional backend callback additionally requires:

```text
HLS_BACKEND_BASE_URL=https://<deployed-backend-host>
```

The backend callback endpoint is implemented in the next migration stage.
Until `HLS_BACKEND_BASE_URL` exists, the workflow safely skips callbacks.

## Manual smoke test

1. Upload a small source video to `R2_RAW_BUCKET`. Use an object key such as:

   ```text
   raw/<lesson-uuid>/<job-uuid>/source.mp4
   ```

2. Push `.github/workflows/hls-transcode.yml` and
   `scripts/hls/transcode.sh` to the default branch.
3. Open the repository's **Actions** tab.
4. Select **HLS Transcode to R2**.
5. Select **Run workflow** and provide:

   ```text
   job_id       A new UUID
   lesson_id    An existing lesson UUID
   source_key   The R2 object key from step 1
   output_prefix Leave empty to use hls/{lesson_id}/{job_id}
   ```

6. After the run succeeds, verify this private R2 layout:

   ```text
   hls/<lesson-uuid>/<job-uuid>/
   |-- master.m3u8
   |-- enc.key
   |-- 480p/
   |   |-- playlist.m3u8
   |   `-- segment_00000.ts
   |-- 720p/
   `-- 1080p/
   ```

Only variants that do not exceed the source height are generated. A video
below 480p produces a `source` variant.

## Operational limits

- One job per lesson runs at a time.
- Each job times out after 330 minutes.
- Segment duration is 10 seconds.
- Output uses H.264 video, AAC audio, and AES-128 HLS encryption.
- GitHub runner disk is temporary; R2 is the durable store.
- Re-running the same job replaces only its versioned output prefix.

Do not point the current backend at a versioned output until the workflow has
completed successfully and the backend callback has atomically marked that
version ready.
