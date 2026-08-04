# Ma trận unit test cho Video AI service

Tài liệu này đối chiếu trực tiếp với source và test hiện tại. Khi source thay đổi làm
số dòng dịch chuyển, hãy dùng **tên method + nội dung nhánh** làm mốc chính.

## Cách chuyển sang Excel

Tạo các cột: `Test ID`, `Test method`, `Dòng source/nhánh`,
`Biến và mock`, `Kết quả mong đợi`. Mỗi dòng trong các bảng dưới đây là một test
case độc lập.

## 1. VideoSummaryServiceTest — 29 test

File test: `src/test/java/com/smartlearnly/backend/videoai/service/VideoSummaryServiceTest.java`

| ID | Test method | Dòng source/nhánh cần đi qua | Biến và mock | Kết quả mong đợi |
|---|---|---|---|---|
| VS-01 | `extractVideoIdFromYoutubeUrl_returnsVideoId_whenUrlIsStandardWatchUrl` | 83–125; host `www.youtube.com`, path `/watch` | `youtubeUrl=WATCH_URL` | Trả `VIDEO_ID` |
| VS-02 | `extractVideoIdFromYoutubeUrl_returnsVideoId_whenWatchUrlHasNoWwwPrefix` | 111–117; nhánh host `youtube.com` | `youtubeUrl=https://youtube.com/watch?v=...` | Trả `VIDEO_ID` |
| VS-03 | `extractVideoIdFromYoutubeUrl_returnsVideoId_whenUrlUsesHttpsPort443` | 93–96; `port != -1` true, `port != 443` false | `port=443` | Trả `VIDEO_ID` |
| VS-04 | `extractVideoIdFromYoutubeUrl_returnsVideoId_whenUrlIsYoutuBeUrl` | 106–110; nhánh `if (youtu.be)` | `youtubeUrl=https://youtu.be/{VIDEO_ID}` | Trả ID từ path |
| VS-05 | `extractVideoIdFromYoutubeUrl_returnsVideoId_whenWatchPathHasTrailingSlash` | 111–117; vế `path=/watch/` | URL có dấu `/` sau `watch` | Trả `VIDEO_ID` |
| VS-06 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsYoutubeShortsUrl` | 106–120; không vào `if/else-if`, `videoId=null` | `path=/shorts/{ID}` | `INVALID_REQUEST` |
| VS-07 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsYoutubeEmbedUrl` | 106–120; path không được hỗ trợ | `path=/embed/{ID}` | `INVALID_REQUEST` |
| VS-08 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesMobileYoutubeHost` | 106–120; host không khớp | `host=m.youtube.com` | `INVALID_REQUEST` |
| VS-09 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesYoutubeMusicHost` | 106–120; host không khớp | `host=music.youtube.com` | `INVALID_REQUEST` |
| VS-10 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesHttp` | 93–99; scheme không phải HTTPS | `scheme=http` | `INVALID_REQUEST` |
| VS-11 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenHttpsUrlHasNoHost` | 93–99; `uri.getHost()==null` | `youtubeUrl=https:/watch?v=...` | `INVALID_REQUEST` |
| VS-12 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlContainsUserInfo` | 93–99; `uri.getUserInfo()!=null` | `userInfo=student` | `INVALID_REQUEST` |
| VS-13 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesNonHttpsPort` | 93–99; port khác `-1` và `443` | `port=8443` | `INVALID_REQUEST` |
| VS-14 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUriSyntaxIsMalformed` | 91–129; `catch URISyntaxException` | URL chứa `%ZZ` | Catch chuyển thành `INVALID_REQUEST` |
| VS-15 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsNull` | 75–88; `value==null`, `normalized==null` | `youtubeUrl=null` | `INVALID_REQUEST` |
| VS-16 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsBlank` | 75–88; trim thành rỗng | `youtubeUrl="   "` | `INVALID_REQUEST` |
| VS-17 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenValueIsNotUrl` | 91–99; URI tương đối có scheme/host null | `youtubeUrl=this-is-not-a-url` | `INVALID_REQUEST` |
| VS-18 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenYoutuBeUrlHasNoVideoId` | 106–120; path không chứa ID | `youtubeUrl=https://youtu.be/` | `INVALID_REQUEST` |
| VS-19 | `extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenVideoIdLengthIsInvalid` | 120–123; regex ID false | `v=short` | `INVALID_REQUEST` |
| VS-20 | `normalizeLessonVideoUrl_returnsCanonicalUrl_whenNewYoutuBeUrlIsProvided` | 50–72; requested mới hợp lệ | `current=null`, `requested=youtu.be`, `videoLesson=true` | URL canonical `/watch?v=` |
| VS-21 | `normalizeLessonVideoUrl_keepsCurrentUrl_whenRequestedUrlIsNull` | 59–62; requested null, current khác null | `current=WATCH_URL`, `requested=null` | Giữ URL hiện tại |
| VS-22 | `normalizeLessonVideoUrl_keepsCurrentUrl_whenRequestedUrlIsUnchanged` | 68–70; `current.equals(requested)` true | Hai URL giống nhau | Giữ URL hiện tại |
| VS-23 | `normalizeLessonVideoUrl_returnsCanonicalRequestedUrl_whenCurrentUrlIsDifferent` | 68–72; current khác null nhưng `equals=false` | current ID khác requested ID | Chuẩn hóa URL requested |
| VS-24 | `normalizeLessonVideoUrl_returnsNull_whenLessonIsNotVideoLesson` | 54–56; `!videoLesson` | `videoLesson=false` | Trả `null` |
| VS-25 | `normalizeLessonVideoUrl_throwsInvalidRequest_whenNewVideoLessonHasNoUrl` | 59–65; current và requested đều null | `videoLesson=true` | `INVALID_REQUEST` |
| VS-26 | `generateVideoSummary_returnsCompleteResponse_whenAllServicesSucceed` | 31–47; luồng thành công | Metadata `1021s`; transcript `vi`; Gemini summary hợp lệ | Response đủ ID, URL, giây, phút=18, summary |
| VS-27 | `generateVideoSummary_propagatesException_whenMetadataValidationFails` | 34; dependency đầu ném lỗi | Mock metadata ném `BUSINESS_RULE_VIOLATION` | Giữ lỗi; không gọi transcript |
| VS-28 | `generateVideoSummary_propagatesException_whenTranscriptRetrievalFails` | 34–36; metadata pass, transcript fail | Metadata `600s`; transcript ném lỗi | Giữ lỗi; không gọi Gemini |
| VS-29 | `generateVideoSummary_propagatesException_whenGeminiGenerationFails` | 34–38; Gemini fail | Metadata/transcript pass; Gemini ném unavailable | Giữ `EXTERNAL_SERVICE_UNAVAILABLE` |

## 2. YoutubeVideoMetadataServiceTest — 21 test

File test: `src/test/java/com/smartlearnly/backend/videoai/service/YoutubeVideoMetadataServiceTest.java`

| ID | Test method | Dòng source/nhánh cần đi qua | Biến và mock | Kết quả mong đợi |
|---|---|---|---|---|
| YM-01 | `fetchYoutubeVideoMetadata_returnsMetadata_whenYoutubeResponseIsValid` | 47–99; mọi điều kiện hợp lệ | items array; `embeddable=true`; `caption=true`; duration hợp lệ | Trả `durationSeconds` |
| YM-02 | `fetchYoutubeVideoMetadata_throwsBusinessRuleViolation_whenVideoIsNotEmbeddable` | 70–76; `!embeddable=true` | `embeddable=false` | `BUSINESS_RULE_VIOLATION` |
| YM-03 | `fetchYoutubeVideoMetadata_throwsBusinessRuleViolation_whenVideoHasNoCaptions` | 79–84; `!captionsAvailable=true` | `caption=false` | `BUSINESS_RULE_VIOLATION` |
| YM-04 | `fetchYoutubeVideoMetadata_throwsBusinessRuleViolation_whenDurationIsZero` | 87–97; `durationSeconds<=0` | duration `PT0S` | `BUSINESS_RULE_VIOLATION` |
| YM-05 | `fetchYoutubeVideoMetadata_throwsBusinessRuleViolation_whenDurationExceedsLimit` | 92–97; `durationSeconds>maximumSeconds` | duration lớn hơn limit | `BUSINESS_RULE_VIOLATION` |
| YM-06 | `fetchYoutubeVideoMetadata_returnsMetadata_whenDurationEqualsLimit` | 94; hai vế false tại boundary | duration bằng đúng maximum | Thành công |
| YM-07 | `fetchYoutubeVideoMetadata_usesOneMinuteMinimum_whenConfiguredLimitIsZero` | 92–94; `Math.max(1,0)` | max minutes `0`, duration `PT1M` | Thành công với minimum 1 phút |
| YM-08 | `fetchYoutubeVideoMetadata_throwsUnavailable_whenDurationFormatIsInvalid` | 87–91 và catch 106–111 | duration không đúng ISO-8601 | Catch `DateTimeException`; unavailable |
| YM-09 | `fetchYoutubeVideoMetadata_throwsResourceNotFound_whenItemsIsEmpty` | 62–66; `items.isEmpty()` | `items=[]` | `RESOURCE_NOT_FOUND` |
| YM-10 | `fetchYoutubeVideoMetadata_throwsResourceNotFound_whenItemsIsNotArray` | 62–66; `!items.isArray()` | `items={}` | `RESOURCE_NOT_FOUND` |
| YM-11 | `fetchYoutubeVideoMetadata_throwsResourceNotFound_whenResponseJsonIsNull` | 59–66; JSON node `null`/không có array | response JSON `null` | `RESOURCE_NOT_FOUND` |
| YM-12 | `fetchYoutubeVideoMetadata_throwsUnavailable_whenResponseJsonIsMalformed` | 59 và catch 106–111 | JSON lỗi | Catch `IOException`; unavailable |
| YM-13 | `fetchYoutubeVideoMetadata_throwsResourceNotFound_whenResponseBodyIsEmpty` | 59–66; body null được thay `{}` | HTTP body rỗng | `RESOURCE_NOT_FOUND` |
| YM-14 | `fetchYoutubeVideoMetadata_throwsUnavailable_whenYoutubeReturnsRateLimit` | catch 100–105 | HTTP `429` | Catch `RestClientResponseException`; unavailable |
| YM-15 | `fetchYoutubeVideoMetadata_throwsUnavailable_whenFeatureIsDisabled` | 115–121; `!enabled` | `enabled=false` | Unavailable; không gọi HTTP |
| YM-16 | `fetchYoutubeVideoMetadata_throwsUnavailable_whenApiKeyIsNull` | 116–121; API key null | `youtubeApiKey=null` | Unavailable |
| YM-17 | `fetchYoutubeVideoMetadata_throwsUnavailable_whenApiKeyIsBlank` | 116–121; API key blank | `youtubeApiKey="   "` | Unavailable |
| YM-18 | `constructor_createsMetadataService_whenTimeoutIsZero` | 131–143; timeout mặc định | timeout `Duration.ZERO` | Constructor thành công |
| YM-19 | `constructor_createsMetadataService_whenTimeoutIsNegative` | 135–139; `timeout.isNegative()` | timeout âm | Dùng 20 giây mặc định |
| YM-20 | `constructor_createsMetadataService_whenTimeoutIsNull` | 135–139; `timeout==null` | timeout null | Dùng 20 giây mặc định |
| YM-21 | `constructor_createsMetadataService_whenTimeoutIsPositive` | 135–139; toàn bộ điều kiện false | timeout dương | Dùng timeout cấu hình |

## 3. YoutubeTranscriptServiceTest — 42 test

File test: `src/test/java/com/smartlearnly/backend/videoai/service/YoutubeTranscriptServiceTest.java`

| ID | Test method | Dòng source/nhánh cần đi qua | Biến và mock | Kết quả mong đợi |
|---|---|---|---|---|
| YT-01 | `parseTranscriptWorkerOutput_returnsPlainTranscript_whenWorkerOutputIsValid` | 84–115; vòng `for` nhiều segment | language `VI`; 2 segment hợp lệ | language `vi`, text được nối bằng khoảng trắng |
| YT-02 | `parseTranscriptWorkerOutput_acceptsTranscript_whenLengthEqualsCharacterLimit` | 104; boundary `length > max` false | text length = max | Thành công |
| YT-03 | `parseTranscriptWorkerOutput_throwsBusinessRuleViolation_whenTranscriptExceedsLimit` | 104–108; điều kiện true | text length > max | `BUSINESS_RULE_VIOLATION` |
| YT-04 | `parseTranscriptWorkerOutput_throwsUnavailable_whenWorkerResultIsNull` | 86–89; `worker==null` | file chứa JSON `null` | Unavailable |
| YT-05 | `parseTranscriptWorkerOutput_throwsUnavailable_whenWorkerFieldsAreMissing` | 87–89; field bắt buộc thiếu | JSON `{}` | Unavailable |
| YT-06 | `parseTranscriptWorkerOutput_throwsUnavailable_whenLanguageIsBlank` | 87–89; `language.isBlank()` | language khoảng trắng | Unavailable |
| YT-07 | `parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentsAreNull` | 87–89; `segments==null` | segments null | Unavailable |
| YT-08 | `parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentsAreEmpty` | 87–89; `segments.isEmpty()` | `segments=[]` | Unavailable |
| YT-09 | `parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentIsNull` | 93–98; `segment==null` | list chứa null | Unavailable |
| YT-10 | `parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentTextIsNull` | 94–98; `segment.text()==null` | text null | Unavailable |
| YT-11 | `parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentTextIsBlank` | 94–98; `text.isBlank()` | text khoảng trắng | Unavailable |
| YT-12 | `parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentStartIsNegative` | 95–98; `start<0` | start `-1` | Unavailable |
| YT-13 | `parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentDurationIsZero` | 95–98; `duration<=0` | duration `0` | Unavailable |
| YT-14 | `parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentDurationIsNegative` | 95–98; `duration<=0` | duration `-1` | Unavailable |
| YT-15 | `parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentStartIsNaN` | 95–98; `!Double.isFinite(start)` | start `"NaN"` | Unavailable |
| YT-16 | `parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentDurationIsInfinite` | 95–98; `!Double.isFinite(duration)` | duration `"Infinity"` | Unavailable |
| YT-17 | `fetchYoutubeTranscript_throwsUnavailable_whenVideoAiIsDisabled` | 117–120 | `enabled=false` | Unavailable trước khi tạo process |
| YT-18 | `fetchYoutubeTranscript_throwsInvalidRequest_whenVideoIdIsNull` | 121–123; `videoId==null` | ID null | `INVALID_REQUEST` |
| YT-19 | `fetchYoutubeTranscript_throwsInvalidRequest_whenVideoIdFormatIsInvalid` | 121–123; regex false | ID sai 11 ký tự | `INVALID_REQUEST` |
| YT-20 | `fetchYoutubeTranscript_throwsUnavailable_whenScriptFileDoesNotExist` | 124–126; `!Files.isRegularFile` | path không tồn tại | Unavailable |
| YT-21 | `fetchYoutubeTranscript_throwsUnavailable_whenScriptPathIsNull` | 124–126; `script==null` | path null | Unavailable |
| YT-22 | `fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandIsBlank` | 155–163; normalized empty | command khoảng trắng | Unavailable |
| YT-23 | `fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandIsNull` | 155–163; toán tử `value==null` | command null | Unavailable |
| YT-24 | `fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandIsTooLong` | 157–161; length > 256 | 257 ký tự | Unavailable |
| YT-25 | `fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandContainsNullByte` | 158–161 | command chứa `\0` | Unavailable |
| YT-26 | `fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandContainsCarriageReturn` | 159–161 | command chứa `\r` | Unavailable |
| YT-27 | `fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandContainsLineFeed` | 160–161 | command chứa `\n` | Unavailable |
| YT-28 | `fetchYoutubeTranscript_returnsTranscript_andCapsTimeoutAtFiveMinutes` | 37–68, 152; nhánh cap timeout | timeout 10 phút; process exit 0; JSON hợp lệ | Timeout thực 5 phút; trả transcript |
| YT-29 | `fetchYoutubeTranscript_usesDefaultTimeout_whenConfiguredTimeoutIsZero` | 147–152; timeout zero | timeout `0` | Dùng 60 giây |
| YT-30 | `fetchYoutubeTranscript_usesDefaultTimeout_whenConfiguredTimeoutIsNegative` | 149–152; timeout âm | duration âm | Dùng 60 giây |
| YT-31 | `fetchYoutubeTranscript_usesDefaultTimeout_whenConfiguredTimeoutIsNull` | 149–152; timeout null | duration null | Dùng 60 giây |
| YT-32 | `fetchYoutubeTranscript_throwsBusinessRuleViolation_whenTranscriptIsDisabled` | 59–64, 130–137 | exit 1; log `TRANSCRIPT_DISABLED` | `BUSINESS_RULE_VIOLATION` |
| YT-33 | `fetchYoutubeTranscript_throwsBusinessRuleViolation_whenTranscriptIsNotFound` | 132–137; vế marker thứ hai | log `TRANSCRIPT_NOT_FOUND` | `BUSINESS_RULE_VIOLATION` |
| YT-34 | `fetchYoutubeTranscript_throwsResourceNotFound_whenVideoIsUnavailable` | 138–140 | log `VIDEO_UNAVAILABLE` | `RESOURCE_NOT_FOUND` |
| YT-35 | `fetchYoutubeTranscript_throwsUnavailable_whenYoutubeBlocksTranscriptRequest` | 141–143 | log `YOUTUBE_BLOCKED` | Unavailable |
| YT-36 | `fetchYoutubeTranscript_throwsUnavailable_whenWorkerReturnsUnknownFailure` | 144 | log không có marker | Unavailable mặc định |
| YT-37 | `fetchYoutubeTranscript_throwsUnavailable_andStopsProcess_whenWorkerTimesOut` | 53–56 | `waitFor=false` | Destroy process; unavailable timeout |
| YT-38 | `fetchYoutubeTranscript_throwsUnavailable_andRestoresInterrupt_whenWaitIsInterrupted` | catch 69–74 | `waitFor` ném `InterruptedException` | Khôi phục interrupt, destroy, unavailable |
| YT-39 | `fetchYoutubeTranscript_throwsUnavailable_whenWorkerCreatesNoOutput` | 65–67; file tồn tại nhưng size 0 | exit 0, không ghi JSON | Unavailable |
| YT-40 | `fetchYoutubeTranscript_throwsUnavailable_whenWorkerDeletesOutputFile` | 65–67; `!Files.isRegularFile` | exit 0, mock xóa output | Unavailable |
| YT-41 | `fetchYoutubeTranscript_throwsUnavailable_whenWorkerCannotStart` | catch 75–77 | `ProcessBuilder.start` ném `IOException` | Catch và trả unavailable |
| YT-42 | `fetchYoutubeTranscript_throwsUnavailable_whenWorkerOutputIsMalformedJson` | 68, catch 75–77 | exit 0, output `{not-json` | Catch `IOException`; unavailable |

## 4. GeminiVideoSummaryServiceTest — 20 test

File test: `src/test/java/com/smartlearnly/backend/videoai/service/GeminiVideoSummaryServiceTest.java`

| ID | Test method | Dòng source/nhánh cần đi qua | Biến và mock | Kết quả mong đợi |
|---|---|---|---|---|
| GM-01 | `generateSummaryFromTranscript_returnsStructuredSummary_whenGeminiResponseIsValid` | 52–114, 133–201 | language `vi`; transcript hợp lệ; response đúng schema | Trim paragraphs/title, bỏ bullet, trả summary |
| GM-02 | `generateSummaryFromTranscript_filtersNullBlankAndBulletOnlyItems` | 204–224; cả nhánh giữ/loại của 2 filter | overview có null/blank; takeaway `"-"` | Loại item rỗng, còn đúng 3 + 3 |
| GM-03 | `generateSummaryFromTranscript_usesDetectedLanguageInstruction_whenLanguageIsNull` | 253–257; ternary true | language null | Prompt dùng `the detected language` |
| GM-04 | `generateSummaryFromTranscript_throwsUnavailable_whenServiceIsDisabled` | 227–234; `!enabled` | enabled false | Unavailable, không HTTP |
| GM-05 | `generateSummaryFromTranscript_throwsUnavailable_whenApiKeyIsNull` | 228–234; API key normalize null | key null | Unavailable |
| GM-06 | `generateSummaryFromTranscript_throwsUnavailable_whenApiKeyIsBlank` | 228–234, 260–270 | key blank | Unavailable |
| GM-07 | `generateSummaryFromTranscript_throwsUnavailable_whenModelIsNull` | 228–242 | model null | Unavailable, không fallback |
| GM-08 | `generateSummaryFromTranscript_throwsInvalidRequest_whenTranscriptIsNull` | 57–61 | transcript null | `INVALID_REQUEST` |
| GM-09 | `generateSummaryFromTranscript_throwsInvalidRequest_whenTranscriptIsBlank` | 57–61, 260–270 | transcript blank | `INVALID_REQUEST` |
| GM-10 | `generateSummaryFromTranscript_throwsUnavailable_withoutFallback_whenGeminiReturnsHttpError` | catch 115–121 | HTTP 400 | Catch HTTP; unavailable; chỉ 1 request |
| GM-11 | `generateSummaryFromTranscript_throwsUnavailable_whenGeminiReturnsNoContent` | 103–114, 169–172, catch 122–128 | HTTP 204/body null | Output null -> `IOException` -> unavailable |
| GM-12 | `generateSummaryFromTranscript_throwsUnavailable_whenProviderEnvelopeIsNotStandard` | 106–114, 169–172 | JSON top-level `text`, thiếu candidates | Unavailable |
| GM-13 | `generateSummaryFromTranscript_throwsUnavailable_whenGeminiReturnsLegacySummary` | 175–186, catch 122–128 | output chỉ có field `summary` | `IOException`/mapping lỗi -> unavailable |
| GM-14 | `generateSummaryFromTranscript_throwsUnavailable_whenSummaryJsonIsMalformed` | 175, catch 122–128 | output `not-json` | Catch parse `IOException`; unavailable |
| GM-15 | `generateSummaryFromTranscript_throwsUnavailable_whenOverviewHasOnlyTwoParagraphs` | 182–186 | paragraphs size 2 | Unavailable |
| GM-16 | `generateSummaryFromTranscript_throwsUnavailable_whenOverviewParagraphsIsNull` | 204–207 rồi 182–186 | overview null | List rỗng; unavailable |
| GM-17 | `generateSummaryFromTranscript_throwsUnavailable_whenTakeawayTitleIsBlank` | 178, 182–186 | title khoảng trắng | Normalize null; unavailable |
| GM-18 | `generateSummaryFromTranscript_throwsUnavailable_whenThereAreOnlyTwoTakeaways` | 184–186 | 2 takeaways | Unavailable |
| GM-19 | `generateSummaryFromTranscript_throwsUnavailable_whenThereAreSixTakeaways` | 185–186 | 6 takeaways | Unavailable |
| GM-20 | `generateSummaryFromTranscript_throwsUnavailable_whenSummaryIsOversized` | vòng for 190–198 | tổng ký tự > 50.000 | `IOException` -> unavailable |

## 5. Đối chiếu try/catch/finally

| Service | Khối cần kiểm tra | Test đi qua |
|---|---|---|
| `VideoSummaryService` | `catch (URISyntaxException \| IllegalArgumentException)` dòng 126–129 | VS-14 đi qua `URISyntaxException`; `IllegalArgumentException` là cùng đường xử lý |
| `YoutubeVideoMetadataService` | try thành công dòng 49–99 | YM-01, YM-06, YM-07 |
| `YoutubeVideoMetadataService` | catch HTTP dòng 100–105 | YM-14 |
| `YoutubeVideoMetadataService` | catch parse/duration dòng 106–111 | YM-08 (`DateTimeException`), YM-12 (`IOException`) |
| `YoutubeTranscriptService` | try thành công dòng 37–68 | YT-28 |
| `YoutubeTranscriptService` | catch interrupt dòng 69–74 | YT-38 |
| `YoutubeTranscriptService` | catch IO dòng 75–77 | YT-41, YT-42 |
| `YoutubeTranscriptService` | finally dòng 78–80 | Mọi test gọi `fetchYoutubeTranscript`; file tạm được dọn cả khi pass/fail |
| `GeminiVideoSummaryService` | try thành công dòng 94–114 | GM-01, GM-02, GM-03 |
| `GeminiVideoSummaryService` | catch HTTP dòng 115–121 | GM-10 |
| `GeminiVideoSummaryService` | catch parse/REST/argument dòng 122–128 | GM-11 đến GM-20 (các lỗi parse/contract) |

## 6. Nhánh phòng thủ không nên tạo test giả

JaCoCo còn báo một số nhánh chưa phủ. Đây không phải thiếu test nghiệp vụ:

| Source | Nhánh | Lý do |
|---|---|---|
| `VideoSummaryService.java:103` | `uri.getPath()==null` | URI HTTPS có host hợp lệ trả path rỗng hoặc path cụ thể; URI opaque đã bị loại vì host null |
| `YoutubeVideoMetadataService.java:62-63` | `response==null` hoặc `items==null` | body null đã đổi thành `{}`; `JsonNode.path()` trả `MissingNode`, không trả null |
| `YoutubeTranscriptService.java:71` | `process==null` trong catch interrupt | `InterruptedException` chỉ phát sinh ở `waitFor` sau khi `process` đã được gán |
| `YoutubeTranscriptService.java:125` | script tồn tại nhưng không readable | Không mô phỏng ổn định trên Windows; null và non-regular đã có YT-20/YT-21 |
| `YoutubeTranscriptService.java:131` | `processLog==null` | `readBoundedLog()` luôn trả chuỗi hoặc ném IOException |
| `YoutubeTranscriptService.java:176-182` | path null/lỗi xóa file | Hàm private defensive cleanup; không dùng reflection chỉ để tăng coverage |

## 7. Kết quả chạy hiện tại

- Tổng: **112 test**.
- Failures: **0**.
- Errors: **0**.
- Skipped: **0**.
- Gemini service: **100% line, 100% branch**.
- VideoSummary service: **100% line**, còn 1 nhánh phòng thủ ở dòng 103.
- Metadata service: **100% line**, còn 2 nhánh null bất khả thi qua API thật.
- Transcript service: thiếu 3 dòng defensive cleanup và 4 nhánh phòng thủ nêu trên.
