# TwoChat 💬

Ứng dụng chat **realtime cho đúng hai người**, viết bằng **Kotlin + Jetpack Compose**
(Android native), backend là **Supabase** (Postgres + Auth + Realtime).

Không có server Node, không có VPS, không có Firebase: app nói chuyện trực tiếp với
project Supabase của **bạn**, dữ liệu nằm trong database của bạn.

```
┌───────────────┐          HTTPS (REST)          ┌──────────────────────────┐
│  Android app  │ ─────────────────────────────▶ │ Supabase                 │
│  Kotlin       │   /auth/v1  ·  /rest/v1        │  · GoTrue   (đăng nhập)  │
│  Compose      │                                │  · PostgREST (dữ liệu)   │
│  Room cache   │ ◀───────────────────────────── │  · Realtime (WebSocket)  │
└───────────────┘        WSS realtime           │  · Postgres + RLS        │
                                                └──────────────────────────┘
```

## Tính năng

| | |
|---|---|
| Nhắn tin tức thời | tin mới hiện ngay bên kia trong ~100 ms (Realtime `postgres_changes`) |
| Đã xem | dấu ✓✓ khi người kia đã đọc tin |
| Đang nhập… | hiện chấm "Đang nhập…" (Realtime Broadcast) |
| Online / ngoại tuyến | "Đang hoạt động" + "hoạt động 5 phút trước" (`last_seen`) |
| Lịch sử | lưu trên Postgres, mở app là thấy ngay (kèm cache Room để xem offline) |
| Gửi không mất tin | tin đang chờ / gửi lỗi / bấm để gửi lại, chống trùng bằng `client_id` |
| Đăng nhập | **email + mật khẩu** hoặc **ẩn danh** (một chạm, không cần email) |
| Ghép đôi | nhập mã 6 ký tự của người kia; mỗi cuộc trò chuyện đúng 2 người |
| Ảnh / tệp | gửi ảnh (tự nén còn ~1600px), tệp bất kỳ tới 25 MB, tải về và mở bằng app khác |
| Tin nhắn thoại | giữ mic để ghi, thả ra là gửi; nghe lại ngay trong bong bóng chat |
| Nhãn dán | bộ 24 emoji gửi một chạm, hiển thị cỡ lớn |
| Sửa / thu hồi | nhấn giữ tin của mình → **Sửa** (có nhãn "đã sửa") hoặc **Thu hồi** (người kia thấy "Tin nhắn đã được thu hồi") |
| Tìm kiếm | icon 🔍 trên header, lọc tin nhắn trong cuộc trò chuyện |
| Thông báo | tin mới khi app ở nền → notification + số đếm chưa đọc trên badge |
| Giao diện | tiếng Việt, sáng/tối, vuốt để cuộn, bong bóng chat, avatar màu |

## 1. Chuẩn bị Supabase (5 phút)

1. Tạo project tại [supabase.com](https://supabase.com) (gói Free là đủ).
2. **SQL Editor → New query**: dán toàn bộ [`supabase/schema.sql`](supabase/schema.sql) → **Run**.
   Script tạo bảng, RLS, RPC ghép đôi, trigger tạo hồ sơ và bật realtime; chạy lại nhiều lần cũng không sao.
3. **Authentication → Sign In / Providers → Allow anonymous sign-ins** → bật (nếu muốn dùng chế độ ẩn danh).
   Nếu tắt, chế độ ẩn danh sẽ báo lỗi `422 Signup requires a valid password`.
4. **Authentication → Sign In / Providers → Email**: nếu muốn đăng ký là dùng được ngay thì tắt
   *Confirm email*. Nếu để bật, người dùng phải mở mail xác nhận trước khi đăng nhập.
5. **Project Settings → API**: copy **Project URL** và **anon public key** — sẽ nhập vào app.

`anon key` là khoá công khai, được thiết kế để nằm trong app. Quyền đọc/ghi do **Row Level
Security** quyết định: mỗi người chỉ đọc được hồ sơ của mình và của người đã ghép đôi, chỉ đọc
được tin nhắn trong cuộc trò chuyện của mình.

## 2. Build APK

Cách nhanh nhất — mở thư mục `android/` bằng Android Studio (JDK 17), chờ Gradle sync rồi
**Run ▶**. Hoặc bằng dòng lệnh:

```bash
cd android
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # unit test
./gradlew assembleRelease      # APK release (chưa ký, cần keystore riêng)
```

GitHub Actions (`.github/workflows/android.yml`) tự build + chạy test mỗi lần push vào `android/**`
và upload `twchat-debug-apk`.

Yêu cầu: JDK 17, Android SDK 34, minSdk 26 (Android 8.0+).

## 3. Dùng app

1. Mở app → nhập **Project URL** + **anon key** (chỉ lần đầu, được lưu lại).
2. Chọn **Đăng nhập**, **Đăng ký** hoặc **Ẩn danh** (ẩn danh chỉ cần tên hiển thị).
3. Màn hình ghép đôi hiện **mã 6 ký tự** của bạn → gửi cho người kia.
   Một trong hai người nhập mã của người còn lại → hai máy vào thẳng cuộc trò chuyện.
4. Hai người nhắn tin, thấy "Đang nhập…", "Đã xem", trạng thái online.
5. ⚙️ Cài đặt: đổi tên/màu, xem kết nối, huỷ ghép đôi (xoá luôn tin nhắn của cả hai), đăng xuất.

Muốn chat với nhau trên cùng một máy để thử? Dùng hai emulator/profile, mỗi bên đăng nhập một
tài khoản (một bên có thể là ẩn danh) rồi ghép mã.

## 4. Kiến trúc

```
android/app/src/main/java/com/twchat/app/
├── data/
│   ├── supabase/
│   │   ├── SupabaseApi.kt        # GoTrue (auth) + PostgREST (dữ liệu), tự refresh token
│   │   ├── SupabaseRealtime.kt   # WebSocket Phoenix: postgres_changes + broadcast
│   │   ├── SupabaseGateway.kt    # cổng duy nhất cho ViewModel
│   │   ├── Models.kt             # TokenSet, RemoteMessage, SupabaseException
│   │   └── SupabaseMapping.kt    # JSON ⇄ model, ISO-8601 ⇄ epoch millis
│   ├── SessionStore.kt           # DataStore: URL, anon key, token, hồ sơ đã cache
│   ├── Person.kt / AppSession.kt # trạng thái phiên (đã cấu hình / đã đăng nhập / đã ghép đôi)
│   ├── Message.kt / MessageDao.kt / ChatDatabase.kt / ChatRepository.kt
├── ui/
│   ├── AppViewModel.kt           # điều phối: auth, realtime, gửi tin, ghép đôi
│   ├── TwoChatApp.kt             # điều hướng giữa các màn hình
│   ├── screens/                  # Auth, Pair, Chat, Settings
│   └── components/               # bong bóng chat, composer, header, avatar…
└── util/                         # định dạng thời gian, transcript để copy
```

**Không dùng SDK Supabase** — chỉ OkHttp + `org.json`, nhờ vậy APK nhẹ và mọi lời gọi đều đọc
được trong `SupabaseApi.kt`.

### Đồng bộ như thế nào

- Gửi tin: ghi vào Room ở trạng thái `pending` → `POST /rest/v1/messages` (upsert theo
  `conversation_id + sender_id + client_id`) → đổi sang `sent`. Lỗi mạng → `failed`, bấm để gửi lại
  với đúng `client_id` nên không bao giờ bị nhân đôi.
- Nhận tin: Realtime `postgres_changes` (INSERT/UPDATE) trên `messages` đã lọc theo
  `conversation_id`; payload được map sang Room, UI tự cập nhật.
- "Đã xem": người nhận `PATCH /rest/v1/messages?read_at=is.null&sender_id=neq.<mình>` → UPDATE
  chảy về máy người gửi qua realtime.
- "Đang nhập…" và presence: Broadcast trên cùng channel (`realtime:chat:<conversation_id>`),
  heartbeat 15 giây/lần, không ghi vào database.
- Dự phòng: nếu WebSocket không kết nối được (mạng chặn, RLS lỗi), app tự hỏi lại tin nhắn mỗi
  5 giây và tự kết nối lại với backoff — chat vẫn chạy, chỉ chậm hơn.

## 5. Dữ liệu

```
profiles(id → auth.users, display_name, color, invite_code unique 6 ký tự,
         conversation_id, last_seen)
conversations(id, member_a < member_b, unique(member_a, member_b))
messages(id identity, conversation_id, sender_id, client_id uuid, body ≤ 4000,
         created_at, read_at, unique(conversation_id, sender_id, client_id))
```

RPC (chạy với quyền definer, tự kiểm tra `auth.uid()`):

- `pair_with_code(code text)` — tìm người có mã, tạo cuộc trò chuyện, gắn cho cả hai.
- `unpair()` — gỡ cả hai, xoá tin nhắn + cuộc trò chuyện.

Muốn xem/sửa dữ liệu: **Table Editor** trong dashboard. Muốn xoá sạch: `truncate public.messages,
public.conversations; update public.profiles set conversation_id = null;`.

## 6. Xử lý sự cố

| Hiện tượng | Nguyên nhân / cách sửa |
|---|---|
| `422 Signup requires a valid password` khi bấm Ẩn danh | chưa bật *Allow anonymous sign-ins* trong Authentication → Providers |
| `Không tìm thấy hồ sơ` | chưa chạy `supabase/schema.sql` (hoặc chạy thiếu phần trigger) |
| Đăng nhập báo sai mật khẩu dù đúng | tài khoản tạo lúc bật *Confirm email* → mở mail xác nhận trước |
| Gửi ảnh/tệp báo lỗi 403 | chưa chạy phần Storage của `schema.sql` (bucket `chat-media` + policy) |
| Gửi tệp báo "quá lớn" | giới hạn 25 MB mỗi tệp (bucket cũng đặt `file_size_limit`) |
| Không ghi được tin nhắn thoại | cần quyền micro — từ chối rồi thì vào Cài đặt Android → Ứng dụng → TwoChat → Quyền |
| Không thấy thông báo | Android 13+ cần quyền thông báo; app chỉ báo khi đang ở nền (chưa có push khi app bị tắt hẳn) |
| Tin nhắn không hiện ngay, chỉ sau vài giây | Realtime chưa bật cho bảng: chạy lại `schema.sql` (phần realtime) hoặc chạy `alter publication supabase_realtime add table public.messages;` |
| `Không có quyền truy cập` (403) | sai/thiếu `anon key`, hoặc đã đổi policy — chạy lại `schema.sql` |
| Mã mời "không đúng" | mã gồm 6 ký tự, không có chữ I/O/0/1; nhập đúng mã đang hiện trên máy người kia |
| Bấm Ẩn danh nhưng muốn giữ tài khoản | tài khoản ẩn danh gắn với máy; đăng xuất là mất, hãy dùng email + mật khẩu nếu cần giữ |

## 6b. Ảnh, tệp, tin nhắn thoại

- Tệp nằm trong bucket **private** `chat-media`, mỗi file trong thư mục `<conversation_id>/`.
  Policy của Storage so thư mục đầu với `my_conversation()`, nên chỉ hai người trong cuộc trò chuyện
  tải được; app tải qua `GET /storage/v1/object/authenticated/...` kèm JWT.
- Ảnh được nén về cạnh dài tối đa 1600px (JPEG 85%) trước khi tải lên, cache trong `cacheDir/media`.
- Tin nhắn thoại: `MediaRecorder` AAC → `.m4a`, gửi kèm `duration_ms`; phát bằng `MediaPlayer`.
- Tin nhắn mang `kind` (`text` / `sticker` / `image` / `file` / `voice`) + metadata đính kèm; sửa và
  thu hồi là **soft delete** (`edited_at`, `deleted_at`) để cả hai bên cùng thấy trạng thái, và
  trigger `guard_message_update` chặn việc sửa tin của người khác.

## 7. Giới hạn đã biết

- Đúng hai người mỗi cuộc trò chuyện (đúng như thiết kế), không nhóm, không danh bạ.
- **Push khi app bị tắt hẳn: chưa có** (cần Firebase/FCM + Edge Function). Hiện thông báo chỉ hiện
  khi tiến trình còn sống ở nền.
- Nội dung tin nhắn **không mã hoá đầu-cuối**; Supabase (bạn) đọc được trong database, và ảnh/tệp
  cũng nằm trên Storage của bạn.
- Chưa có gọi thoại/video (cần WebRTC + TURN server).
- Lịch sử tải 300 tin gần nhất mỗi lần mở cuộc trò chuyện; tìm kiếm chỉ trên 300 tin đã tải.
