# QR Scanner App – Máy Keyence Android

Ứng dụng quét QR code dành cho máy barcode **Keyence BT series (Android)**, lưu dữ liệu offline và xuất file **Excel (.xlsx)**.

---

## 📁 Cấu trúc project

```
QRScanApp/
├── app/
│   ├── build.gradle                          ← Dependencies (POI, ZXing, Room...)
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/keyence/qrscan/
│       │   ├── MainActivity.kt               ← Màn hình chính (quét + danh sách)
│       │   ├── SettingsActivity.kt           ← Cấu hình định dạng QR
│       │   ├── HistoryActivity.kt            ← Lịch sử các phiên cũ
│       │   ├── ScanItem.kt                   ← Data class + Room Entity
│       │   ├── AppDatabase.kt                ← Room DB + DAO
│       │   ├── QRParser.kt                   ← Parse nội dung QR
│       │   ├── ExcelExporter.kt              ← Xuất file .xlsx (Apache POI)
│       │   └── ScanAdapter.kt                ← RecyclerView adapter
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── activity_settings.xml
│           │   ├── activity_history.xml
│           │   └── item_scan.xml
│           ├── menu/main_menu.xml
│           ├── values/
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── xml/file_paths.xml            ← FileProvider paths
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## 🚀 Hướng dẫn cài đặt & build

### Yêu cầu
| Công cụ | Phiên bản |
|---|---|
| Android Studio | Hedgehog (2023.1) trở lên |
| JDK | 11 hoặc 17 |
| Android SDK | API 26+ (Android 8.0) |
| Gradle | 8.1 |

### Bước 1 – Mở project
1. Mở **Android Studio**
2. Chọn **File → Open** → chọn thư mục `QRScanApp`
3. Đợi Gradle sync xong

### Bước 2 – Kết nối máy Keyence
1. Bật **Developer Options** trên máy Keyence:
   - Vào **Settings → About → Build Number** → bấm 7 lần
2. Bật **USB Debugging**
3. Cắm cáp USB vào PC
4. Android Studio sẽ nhận ra thiết bị

### Bước 3 – Build & cài
```bash
# Build APK release
./gradlew assembleRelease

# Hoặc bấm nút ▶ Run trong Android Studio
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Bước 4 – Cài APK thủ công (không cần PC)
```bash
adb install app-debug.apk
```

---

## 📱 Hướng dẫn sử dụng

### Màn hình chính
```
┌─────────────────────────────────────┐
│  QR Scanner – Keyence    [📋] [⚙️]  │
│  Phiên: 08:30  22/03/2025           │
│  Tổng: 15 sản phẩm | 5 loại        │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ SKU-001          [−] 3 [+] [✕] │ │
│ │ Sản phẩm A                     │ │
│ │ Lô: L2024-01                   │ │
│ │ 08:35:12  22/03/2025           │ │
│ └─────────────────────────────────┘ │
│ ...                                 │
├─────────────────────────────────────┤
│ [📷 Camera] [🗑 Xoá] [📥 Xuất Excel]│
└─────────────────────────────────────┘
```

### Cách quét
| Phương thức | Mô tả |
|---|---|
| **Hardware trigger** (chính) | Bấm nút trigger vật lý trên máy Keyence – data tự động nhận |
| **Camera** (dự phòng) | Bấm nút 📷 để dùng camera ZXing |

### Logic quét thông minh
- Quét **SKU + Lô giống nhau** → **tự động +1 số lượng** (không tạo dòng trùng)
- Quét **SKU mới** → tạo dòng mới
- Có thể sửa số lượng thủ công bằng nút **[−]** / **[+]**

---

## 🔧 Cấu hình định dạng QR

Vào menu **⚙️ Cài đặt** để cấu hình. Hỗ trợ 2 định dạng:

### Định dạng 1: JSON
```json
{"sku": "SKU-001", "name": "Sản phẩm A", "lot": "L2024-01"}
```
Cấu hình key tương ứng trong Settings nếu tên key khác mặc định.

### Định dạng 2: Ký tự phân cách (Delimited)
```
SKU-001|Sản phẩm A|L2024-01
```
Cấu hình:
- **Ký tự phân cách**: `|` (pipe), `,` (comma), `;` (semicolon), `\t` (tab)...
- **Vị trí cột**: SKU=0, Tên=1, Lot=2 (đếm từ 0)

---

## 📊 File Excel xuất ra

File `.xlsx` được lưu tại:
```
/sdcard/Android/data/com.keyence.qrscan/files/Download/
QRScan_<sessionId>_<timestamp>.xlsx
```

| STT | Mã SP (SKU) | Tên Sản Phẩm | Số Lô | Số Lượng | Ngày Giờ Quét |
|---|---|---|---|---|---|
| 1 | SKU-001 | Sản phẩm A | L2024-01 | 5 | 22/03/2025 08:35:12 |
| 2 | SKU-002 | Sản phẩm B | L2024-02 | 3 | 22/03/2025 08:36:40 |
| | | | **TỔNG** | **8** | |

---

## 🔌 Tích hợp máy Keyence

### Keyence BT-A500 / BT-1500 series
Máy gửi data scanner qua **HID Keyboard input**. App đã xử lý 2 cách:

1. **`dispatchKeyEvent()`** – intercept KeyEvent trực tiếp (ưu tiên)
2. **Hidden EditText** – nhận chuỗi kết thúc bằng Enter (fallback)

### Nếu máy không nhận được data
1. Kiểm tra **Input Mode** của Keyence: phải là **Keyboard Wedge** hoặc **HID**
2. Vào **Keyence BT Settings → Scanner → Output mode → Keyboard**
3. Đảm bảo app đang ở foreground (có focus)

---

## 🛠 Thư viện sử dụng

| Thư viện | Mục đích |
|---|---|
| **Apache POI 5.2.3** | Tạo file Excel .xlsx |
| **Room 2.6** | Database SQLite offline |
| **ZXing Android Embedded 4.3** | Quét QR bằng camera |
| **Kotlin Coroutines** | Xử lý bất đồng bộ |
| **Material Components** | UI Material Design |

---

## ❓ Câu hỏi thường gặp

**Q: Máy Keyence model nào tương thích?**
A: Tất cả máy chạy Android 8.0+ với scanner HID mode:
BT-A500, BT-W150, BT-W200, BT-1500, BT-600 series.

**Q: File Excel lưu ở đâu?**
A: `/sdcard/Android/data/com.keyence.qrscan/files/Download/`
Dùng File Manager hoặc kết nối USB để lấy file.

**Q: Mỗi lần mở app có tạo phiên mới không?**
A: Có. Mỗi lần mở app = 1 phiên mới. Phiên cũ xem trong menu **📋 Lịch sử**.

**Q: QR của tôi có thêm trường khác ngoài SKU/Tên/Lot?**
A: Trường đó sẽ được đọc nhưng không xuất. Nếu cần thêm cột, chỉnh `ExcelExporter.kt` thêm cột tương ứng.
