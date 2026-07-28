# Hướng dẫn cài đặt YouTube Video Summary trên Windows

Tài liệu này hướng dẫn cách chạy tính năng YouTube Video Summary khi chuyển
backend sang một máy Windows khác.

Luồng hoạt động:

```text
URL YouTube
→ Java lấy metadata video
→ Java gọi fetch-youtube-transcript.py
→ Python lấy caption/transcript
→ Java gửi transcript cho Gemini
→ API trả video summary dạng JSON
```

## 1. Những file phải có trong project

Các file sau phải được lưu trong Git để máy khác nhận được khi clone hoặc pull:

```text
scripts/video-ai/fetch-youtube-transcript.py
scripts/video-ai/requirements.txt
scripts/video-ai/README.md
```

Không đưa các file sau lên Git:

```text
.venv-video-ai/
__pycache__/
*.pyc
transcript-test.json
```

Mỗi máy phải tự tạo lại `.venv-video-ai`. Không copy virtual environment từ
máy cũ vì nó chứa đường dẫn và file thực thi phụ thuộc máy.

## 2. Phần mềm cần cài trên máy Windows mới

Cần cài:

- Git.
- Python 3, khuyến nghị Python 3.11 hoặc 3.12.
- JDK 17.
- PowerShell.

Mở PowerShell và kiểm tra:

```powershell
git --version
python --version
java -version
```

Nếu lệnh `python` không hoạt động nhưng máy có Python Launcher, kiểm tra:

```powershell
py --version
```

Backend phải chạy bằng JDK 17. Ví dụ đường dẫn cài đặt:

```text
C:\Program Files\Java\jdk-17
```

## 3. Mở PowerShell tại backend repository

Ví dụ:

```powershell
Set-Location "C:\duong-dan-cua-ban\smart-learnly-backend"
```

Kiểm tra đang đứng đúng thư mục:

```powershell
Test-Path ".\mvnw.cmd"
Test-Path ".\scripts\video-ai\fetch-youtube-transcript.py"
Test-Path ".\scripts\video-ai\requirements.txt"
```

Cả ba lệnh phải trả:

```text
True
```

## 4. Tạo Python virtual environment

Nếu lệnh `python` hoạt động:

```powershell
python -m venv .venv-video-ai
```

Nếu chỉ có Python Launcher:

```powershell
py -3.11 -m venv .venv-video-ai
```

Sau khi tạo xong, Python riêng của tính năng nằm tại:

```text
.venv-video-ai\Scripts\python.exe
```

Kiểm tra:

```powershell
.\.venv-video-ai\Scripts\python.exe --version
```

## 5. Cài Python dependency

Nâng cấp `pip`:

```powershell
.\.venv-video-ai\Scripts\python.exe -m pip install --upgrade pip
```

Cài dependency đã khóa phiên bản trong `requirements.txt`:

```powershell
.\.venv-video-ai\Scripts\python.exe -m pip install `
    -r .\scripts\video-ai\requirements.txt
```

Kiểm tra package:

```powershell
.\.venv-video-ai\Scripts\python.exe -m pip show youtube-transcript-api
```

Project hiện sử dụng:

```text
youtube-transcript-api==1.2.4
```

## 6. Test riêng file Python

Test script trước khi chạy Java backend:

```powershell
.\.venv-video-ai\Scripts\python.exe `
    .\scripts\video-ai\fetch-youtube-transcript.py `
    --video-id "V9i3cGD-mts" `
    --output ".\transcript-test.json"
```

Đọc kết quả:

```powershell
Get-Content -Raw ".\transcript-test.json"
```

Có thể format JSON để dễ đọc:

```powershell
Get-Content -Raw ".\transcript-test.json" `
    | ConvertFrom-Json `
    | ConvertTo-Json -Depth 5
```

Kết quả thành công có cấu trúc:

```json
{
  "language": "en",
  "segments": [
    {
      "start": 0.0,
      "duration": 3.2,
      "text": "Nội dung caption..."
    }
  ]
}
```

Các giá trị `start`, `duration`, `text` phụ thuộc video thực tế.

## 7. Cấu hình biến môi trường cho backend

Các biến dưới đây chỉ tồn tại trong cửa sổ PowerShell hiện tại. Phải chạy
backend trong cùng cửa sổ sau khi thiết lập.

Lấy đường dẫn tuyệt đối của backend:

```powershell
$backendRoot = (Resolve-Path ".").Path
```

Chỉ định đúng Python trong virtual environment:

```powershell
$env:APP_VIDEO_AI_PYTHON_COMMAND = `
    Join-Path $backendRoot ".venv-video-ai\Scripts\python.exe"
```

Chỉ định đúng transcript script:

```powershell
$env:APP_VIDEO_AI_TRANSCRIPT_SCRIPT_PATH = `
    Join-Path $backendRoot "scripts\video-ai\fetch-youtube-transcript.py"
```

Bật tính năng:

```powershell
$env:APP_VIDEO_AI_ENABLED = "true"
$env:APP_VIDEO_AI_GENERATION_ENABLED = "true"
```

Cấu hình YouTube Data API key:

```powershell
$env:YOUTUBE_API_KEY = "thay-bang-youtube-api-key-cua-ban"
```

Cấu hình Gemini API key:

```powershell
$env:VIDEO_AI_GEMINI_API_KEY = "thay-bang-gemini-api-key-cua-ban"
```

Cấu hình JDK 17:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
```

Không ghi API key thật vào:

- Source code.
- `application.yml`.
- `README.md`.
- Git commit.
- Ảnh chụp màn hình hoặc log gửi cho người khác.

## 8. Kiểm tra cấu hình trước khi chạy

Kiểm tra file Python:

```powershell
Test-Path $env:APP_VIDEO_AI_PYTHON_COMMAND
```

Kiểm tra transcript script:

```powershell
Test-Path $env:APP_VIDEO_AI_TRANSCRIPT_SCRIPT_PATH
```

Hai lệnh phải trả:

```text
True
```

Kiểm tra JDK:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
```

Kết quả phải là Java 17.

Không dùng lệnh in giá trị API key ra terminal.

## 9. Chạy backend

Trong cùng cửa sổ PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend mặc định chạy tại:

```text
http://localhost:8080
```

Endpoint tạo summary:

```text
POST /api/v1/video-summary/generate
```

Request body:

```json
{
  "youtubeUrl": "https://www.youtube.com/watch?v=V9i3cGD-mts"
}
```

Request cần Bearer Token của tài khoản có một trong các role:

```text
ADMIN
TMO
SME
TRAINER
```

## 10. Cấu hình mặc định trong application.yml

Backend hiện đọc các cấu hình:

```yaml
app:
  video-ai:
    enabled: ${APP_VIDEO_AI_ENABLED:true}
    youtube-api-key: ${YOUTUBE_API_KEY:}
    python-command: ${APP_VIDEO_AI_PYTHON_COMMAND:python}
    transcript-script-path: ${APP_VIDEO_AI_TRANSCRIPT_SCRIPT_PATH:scripts/video-ai/fetch-youtube-transcript.py}
    transcript-timeout: ${APP_VIDEO_AI_TRANSCRIPT_TIMEOUT:PT60S}
    max-video-duration-minutes: ${APP_VIDEO_AI_MAX_VIDEO_DURATION_MINUTES:120}
    max-transcript-characters: ${APP_VIDEO_AI_MAX_TRANSCRIPT_CHARACTERS:100000}
    generation:
      enabled: ${APP_VIDEO_AI_GENERATION_ENABLED:true}
      api-key: ${VIDEO_AI_GEMINI_API_KEY:${GEMINI_API_KEY:}}
```

Tên biến chính xác là:

```text
APP_VIDEO_AI_PYTHON_COMMAND
APP_VIDEO_AI_TRANSCRIPT_SCRIPT_PATH
```

Không sử dụng tên cấu hình cũ:

```text
APP_VIDEO_AI_TRANSCRIPTION_PYTHON_COMMAND
```

## 11. Lỗi thường gặp

### `python` is not recognized

Nguyên nhân:

- Python chưa được cài.
- Python chưa nằm trong `PATH`.

Cách xử lý:

```powershell
py --version
```

Nếu `py` hoạt động, dùng:

```powershell
py -3.11 -m venv .venv-video-ai
```

### `No module named youtube_transcript_api`

Backend đang dùng sai Python hoặc dependency chưa được cài.

Kiểm tra:

```powershell
& $env:APP_VIDEO_AI_PYTHON_COMMAND `
    -m pip show youtube-transcript-api
```

Nếu chưa có package:

```powershell
& $env:APP_VIDEO_AI_PYTHON_COMMAND `
    -m pip install -r .\scripts\video-ai\requirements.txt
```

### `YouTube transcript script is unavailable`

Kiểm tra:

```powershell
Test-Path $env:APP_VIDEO_AI_TRANSCRIPT_SCRIPT_PATH
```

Nếu trả `False`, thiết lập lại:

```powershell
$env:APP_VIDEO_AI_TRANSCRIPT_SCRIPT_PATH = `
    Join-Path (Resolve-Path ".").Path `
    "scripts\video-ai\fetch-youtube-transcript.py"
```

### `YOUTUBE_BLOCKED`

YouTube chặn request từ IP hiện tại. Đây không phải lỗi thiếu package Python.

Có thể thử:

- Chạy lại sau.
- Kiểm tra mạng hoặc firewall.
- Thử bằng một kết nối mạng khác.

### `TRANSCRIPT_DISABLED` hoặc `TRANSCRIPT_NOT_FOUND`

Video không có transcript khả dụng. Hãy chọn video khác có caption.

### `VIDEO_UNAVAILABLE`

Video không tồn tại, bị private, bị xóa hoặc không khả dụng tại khu vực hiện tại.

### `YouTube summary is not configured`

Kiểm tra:

```text
APP_VIDEO_AI_ENABLED
YOUTUBE_API_KEY
```

### `AI summary generation is not configured`

Kiểm tra:

```text
APP_VIDEO_AI_GENERATION_ENABLED
VIDEO_AI_GEMINI_API_KEY
```

### Maven báo không có compiler

Terminal đang sử dụng JRE hoặc Java cũ thay vì JDK 17.

Thiết lập lại:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
```

Sau đó kiểm tra:

```powershell
& "$env:JAVA_HOME\bin\javac.exe" -version
```

## 12. Checklist chuyển sang máy Windows khác

- [ ] Clone hoặc pull đầy đủ source code.
- [ ] Có `fetch-youtube-transcript.py`.
- [ ] Có `requirements.txt`.
- [ ] Cài Python 3.
- [ ] Tạo `.venv-video-ai`.
- [ ] Cài dependency từ `requirements.txt`.
- [ ] Test riêng file Python thành công.
- [ ] Cài JDK 17.
- [ ] Thiết lập `APP_VIDEO_AI_PYTHON_COMMAND`.
- [ ] Thiết lập `APP_VIDEO_AI_TRANSCRIPT_SCRIPT_PATH`.
- [ ] Thiết lập `YOUTUBE_API_KEY`.
- [ ] Thiết lập `VIDEO_AI_GEMINI_API_KEY`.
- [ ] Chạy backend trong cùng PowerShell.
- [ ] Login và lấy access token.
- [ ] Test `/api/v1/video-summary/generate` bằng Postman.

