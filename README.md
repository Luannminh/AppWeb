# TwoChat — app chat riêng cho hai người (Android, offline)

Một ứng dụng Android native viết bằng **Kotlin + Jetpack Compose**: cuộc trò chuyện
1-1 chỉ của hai người, **toàn bộ dữ liệu nằm trên máy** (Room/SQLite + DataStore).
Không tài khoản, không máy chủ, không quyền mạng.

> Ứng dụng nằm trong thư mục [`android/`](android/). Đây là project Gradle mở trực
> tiếp bằng Android Studio.

## Tính năng

| | |
|---|---|
| 💬 **Chat hai chiều** | Bong bóng tin nhắn trái/phải, tách ngày (Hôm nay / Hôm qua / Ngày 3 tháng 6) |
| 🙋 **Đổi người gửi** | Nhấn vào dòng “Gửi với tên …” ngay trên ô nhập để chuyển giữa hai người — phù hợp khi cả hai dùng chung một máy |
| ❤️ **Thả cảm xúc** | Nhấn giữ một tin nhắn → thả ❤️ 😂 😍 😢 👍, sao chép hoặc xoá |
| 🔍 **Tìm kiếm** | Lọc tin nhắn ngay trong cuộc trò chuyện |
| 👤 **Cá nhân hoá** | Tên + màu chủ đạo riêng cho từng người, có màn hình chào lần đầu |
| 📤 **Xuất / chia sẻ** | Kết xuất toàn bộ hội thoại ra văn bản và chia sẻ qua ứng dụng khác |
| 🌙 **Sáng / tối** | Theo cài đặt hệ thống, cùng bảng màu ấm |
| 🔒 **Riêng tư** | Không có `INTERNET` permission, không analytics, gỡ app là mất dữ liệu |

## Công nghệ

- Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00), Material 3
- Room 2.6.1 + KSP — lưu tin nhắn
- DataStore Preferences — lưu hồ sơ hai người
- ViewModel + StateFlow, kiến trúc một chiều (UI state duy nhất)
- `minSdk 26`, `targetSdk 34`, Java 17

## Mở / build

```bash
# Cách 1: Android Studio (Ladybug trở lên, JDK 17)
#   File → Open… → chọn thư mục android/  → Sync → Run 'app'

# Cách 2: dòng lệnh (cần Android SDK + JDK 17)
cd android
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # cài lên máy/emulator đang kết nối
./gradlew test                   # unit test (định dạng ngày giờ, gom nhóm tin nhắn)
```

Workflow [`.github/workflows/android.yml`](.github/workflows/android.yml) build APK
debug trên GitHub Actions và đính kèm vào Artifacts của mỗi lần chạy.

## Cấu trúc

```
android/app/src/main/java/com/twchat/app/
├── MainActivity.kt              # điểm vào, edge-to-edge
├── data/                        # lớp dữ liệu thuần Kotlin
│   ├── Message.kt               # entity Room + hằng số người gửi
│   ├── MessageDao.kt            # truy vấn
│   ├── ChatDatabase.kt          # Room database (singleton)
│   ├── ChatRepository.kt        # cửa duy nhất cho tầng UI
│   ├── Profile.kt               # hồ sơ hai người
│   └── ProfileStore.kt          # DataStore
├── ui/
│   ├── AppViewModel.kt          # state duy nhất + hành động
│   ├── ChatItems.kt             # gom tin nhắn thành dòng danh sách (+ chèn tách ngày)
│   ├── TwoChatApp.kt            # điều hướng màn hình
│   ├── components/              # Avatar, bong bóng, ô nhập, header, sheet cảm xúc…
│   ├── screens/                 # Onboarding, Chat, Settings
│   └── theme/                   # màu, typography, shape
└── util/                        # TimeFormat, Transcript (xuất văn bản)
```

## Ghi chú

- Vì dữ liệu chọn **offline**, app không gửi tin qua mạng: hai người dùng chung máy
  (hoặc dùng như một cuốn nhật ký trò chuyện). Muốn đồng bộ hai điện thoại sau này,
  chỉ cần thay phần thân của `ChatRepository` bằng một nguồn có server — UI không
  phải sửa.
- Mã nguồn được viết trong môi trường không có Android SDK/JDK nên **chưa được biên
  dịch thử** ở đây; hãy chạy `./gradlew test` và `assembleDebug` ở máy có Android
  Studio để xác nhận. Phiên bản thư viện đã chọn là cặp tương thích ổn định
  (Kotlin 1.9.24 ↔ Compose compiler 1.5.14 ↔ AGP 8.5.2 ↔ Gradle 8.7).
- Xem trước thiết kế (bản mô phỏng HTML, không phải app): [`docs/preview.html`](docs/preview.html).
