# TwoChat — nhắn tin thời gian thực cho hai người

Messenger thu nhỏ, tự host: một **app Android native** (Kotlin + Jetpack Compose) và một
**server Node.js** (REST + WebSocket) chạy trên máy của bạn. Hai người đăng ký tài khoản,
ghép đôi bằng mã mời, rồi nhắn tin realtime: tin đến ngay, có “Đã xem”, “Đang nhập…”,
trạng thái online, gửi lỗi thì bấm gửi lại.

```
android/   app Android (Kotlin, Compose, Room cache, OkHttp + WebSocket)
server/    Node.js: REST API + WebSocket + web client chạy luôn trên server
```

## Tính năng

| | |
|---|---|
| ⚡ **Realtime** | Tin nhắn đi qua WebSocket, hiện ngay ở máy kia (< 1 giây), có tự kết nối lại |
| ✓✓ **Đã xem** | Server ghi nhận người nhận đã đọc tới tin nào, hiện “Đã xem” dưới tin của bạn |
| ✍️ **Đang nhập…** | Báo cho người kia biết bạn đang gõ, tự tắt sau ~3 giây |
| 🟢 **Online / last seen** | Chấm xanh trong header, “Hoạt động 5 phút trước” khi offline |
| 📨 **Gửi tin cậy** | Tin hiện ngay (Đang gửi…), `clientId` chống trùng khi retry, lỗi mạng thì “Gửi lại” |
| 💾 **Cache offline** | Room giữ hội thoại trên máy: mở app là thấy lịch sử, mất mạng vẫn đọc được |
| 🔗 **Ghép đôi bằng mã** | Mỗi tài khoản có mã 6 ký tự, nhập mã của người kia là xong |
| 👤 **Hồ sơ** | Tên hiển thị + màu riêng, đổi được trong Cài đặt |
| 🌙 **Sáng / tối** | Theo cài đặt hệ thống |
| 🧾 **Xuất hội thoại** | Kết xuất văn bản và chia sẻ qua app khác |
| 🌐 **Web client** | Server tự phục vụ một bản web (cùng API) để chat thử trên trình duyệt |

Chưa có (cố ý, để giữ code gọn): gửi ảnh/file, thông báo đẩy khi app bị tắt (cần FCM),
gọi thoại, nhóm nhiều người.

## Chạy server

```bash
cd server
npm install
npm start          # http://0.0.0.0:3000  (web client ở "/")
npm run smoke      # test đầu-cuối: đăng ký, ghép đôi, tin nhắn, đã xem, typing, presence
```

Biến môi trường: `PORT` (mặc định 3000), `HOST`, `DATA_FILE` (mặc định `server/data/twochat.json`).

Docker:

```bash
cd server
docker build -t twochat-server .
docker run -p 3000:3000 -v twochat-data:/data twochat-server
```

- Mã nguồn: `server/src/index.js` (routes + WebSocket), `store.js` (lưu JSON), `auth.js` (scrypt + token).
- Dữ liệu là một file JSON, ghi sau mỗi thay đổi (debounce) — đủ cho 2 người, dễ backup.
- Web client: `server/public/` (HTML/CSS/JS thuần, không build step).

## Chạy app Android

```bash
# Android Studio (Ladybug+, JDK 17): File → Open… → chọn thư mục android/ → Run 'app'
cd android && ./gradlew assembleDebug    # APK: app/build/outputs/apk/debug/
./gradlew test                            # unit test: gom nhóm theo ngày, định dạng giờ…
```

**Địa chỉ máy chủ** nhập ngay trên màn hình đăng nhập (mục “Máy chủ”):

| Thiết bị | Địa chỉ |
|---|---|
| Emulator | `http://10.0.2.2:3000` (mặc định) |
| Máy thật cùng Wi-Fi | `http://<IP LAN của máy tính>:3000`, ví dụ `http://192.168.1.10:3000` |
| Server công khai | `https://chat.example.com` (nên dùng HTTPS → app tự dùng `wss://`) |

App đã bật `usesCleartextTraffic` để chat được với server HTTP trong LAN khi phát triển.
Khi đưa lên Internet, hãy đặt sau reverse proxy có HTTPS (Caddy/Nginx) và **tắt** cleartext.

### Thử nhanh trong trình duyệt

Web client ở địa chỉ của server. Mở hai cửa sổ (một cửa sổ ẩn danh), tạo hai tài khoản,
nhập mã mời của nhau, rồi chat — realtime, “Đã xem”, “Đang nhập…” hoạt động y như app.

## API

Tất cả dưới `/api`, JSON, xác thực bằng `Authorization: Bearer <token>`.

| Method | Path | Việc |
|---|---|---|
| POST | `/auth/register` | `{username, password, displayName}` → `{token, user, partner}` |
| POST | `/auth/login` | `{username, password}` → `{token, user, partner}` |
| POST | `/auth/logout` | huỷ token hiện tại |
| GET | `/me` | `{user, partner}` |
| GET | `/pair/code` | mã mời của bạn |
| POST | `/pair` | `{code}` → ghép đôi |
| POST | `/pair/reset` | huỷ ghép đôi (xoá hội thoại) |
| GET | `/conversation` | `{user, partner, messages[]}` (400 tin gần nhất) |
| POST | `/messages` | `{body, clientId}` → tin đã lưu (clientId chống trùng) |
| POST | `/read` | `{lastMessageId}` → đánh dấu đã xem |
| POST | `/typing` | báo đang nhập |
| POST | `/profile` | `{displayName?, color?}` |
| GET | `/health` | kiểm tra sống |

WebSocket: `ws(s)://<host>/ws?token=<token>`. Server gửi `hello`, `message`, `read`,
`typing`, `presence`, `paired`, `unpaired`, `profile`; client gửi `typing`, `read`, `ping`.

## Kiểm thử

- `server/test/smoke.mjs` — 16 bước end-to-end (REST + WebSocket), chạy bằng `npm run smoke`.
- `android/app/src/test/` — JUnit: `ChatItemsTest`, `TimeFormatTest`, `RelativeTimeTest`.
- GitHub Actions: `.github/workflows/server.yml` (smoke test) và `android.yml` (unit test + APK debug).

## Ghi chú

- App chưa được biên dịch trong môi trường soạn code này (không có Android SDK). Nếu
  `./gradlew assembleDebug` báo lỗi, gửi log để sửa — server đã được chạy và test thật.
- Bản quyền hình ảnh/emoji không liên quan; icon app là vector tự vẽ.
- Muốn có thông báo khi app đóng: thêm Firebase Cloud Messaging ở server (gửi push khi có
  tin mới) và đăng ký token ở app — kiến trúc hiện tại đã tách sẵn phần gửi tin trong
  `ChatRepository`/`AppViewModel` để gắn vào.
