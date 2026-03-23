package com.keyence.qrscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.keyence.qrscan.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: ScanAdapter

    // Mỗi lần mở app = 1 phiên mới
    private val sessionId = System.currentTimeMillis()

    // Buffer nhận ký tự từ Keyence hardware scanner (HID Keyboard mode)
    private val scanBuffer = StringBuilder()

    // ZXing camera launcher (dự phòng)
    private val cameraLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        result.contents?.let { saveQR(it.trim()) }
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraScanner()
        else Toast.makeText(this, "Cần quyền camera để quét", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        db = AppDatabase.getInstance(this)

        setupRecyclerView()
        setupButtons()
        observeScans()

        val startTime = java.text.SimpleDateFormat("HH:mm  dd/MM/yyyy",
            java.util.Locale.getDefault()).format(java.util.Date(sessionId))
        binding.tvSession.text = "Phiên: $startTime"

        // Focus để nhận HID input ngay khi mở app
        binding.root.requestFocus()
    }

    // ── RecyclerView ──────────────────────────────────────────────────────
    private fun setupRecyclerView() {
        adapter = ScanAdapter(
            onDeleteClick = { item ->
                AlertDialog.Builder(this)
                    .setTitle("Xoá dòng này?")
                    .setMessage(item.rawQr.take(80))
                    .setPositiveButton("Xoá") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { db.scanDao().delete(item) }
                    }
                    .setNegativeButton("Huỷ", null).show()
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    // ── Nút bấm ──────────────────────────────────────────────────────────
    private fun setupButtons() {
        binding.btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) startCameraScanner()
            else cameraPermission.launch(Manifest.permission.CAMERA)
        }

        binding.btnExport.setOnClickListener { exportExcel() }

        binding.btnClear.setOnClickListener {
            if (adapter.itemCount == 0) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Xoá toàn bộ phiên?")
                .setMessage("${adapter.itemCount} dòng sẽ bị xoá.")
                .setPositiveButton("Xoá hết") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.scanDao().deleteSession(sessionId)
                    }
                }
                .setNegativeButton("Huỷ", null).show()
        }
    }

    // ── Observe DB ────────────────────────────────────────────────────────
    private fun observeScans() {
        lifecycleScope.launch {
            db.scanDao().getBySession(sessionId).collectLatest { items ->
                adapter.submitList(items)
                binding.tvCount.text = "Tổng: ${items.size} lần quét"
                binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    // ── Lưu QR vào DB ────────────────────────────────────────────────────
    private fun saveQR(raw: String) {
        if (raw.isBlank()) return

        lifecycleScope.launch {
            // Kiểm tra trùng trong phiên hiện tại
            val isDup = withContext(Dispatchers.IO) {
                db.scanDao().findDuplicate(sessionId, raw) != null
            }

            withContext(Dispatchers.IO) {
                db.scanDao().insert(ScanItem(sessionId = sessionId, rawQr = raw))
            }

            if (isDup) {
                // Vẫn lưu nhưng cảnh báo trùng (giống conditional formatting đỏ)
                Snackbar.make(binding.root, "⚠️ Trùng: ${raw.take(40)}...", Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(0xFFEF5350.toInt()).show()
            } else {
                binding.tvLastScan.text = "✓ ${raw.take(60)}"
                binding.recyclerView.scrollToPosition(0)
            }
        }
    }

    // ── Camera scanner ─────────────────────────────────────────────────────
    private fun startCameraScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(
                ScanOptions.QR_CODE, ScanOptions.DATA_MATRIX, ScanOptions.CODE_128,
                ScanOptions.CODE_39, ScanOptions.EAN_13
            )
            setPrompt("Hướng camera vào mã vạch")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        cameraLauncher.launch(options)
    }

    // ── Xuất Excel ─────────────────────────────────────────────────────────
    private fun exportExcel() {
        if (adapter.itemCount == 0) {
            Toast.makeText(this, "Chưa có dữ liệu để xuất", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val items = withContext(Dispatchers.IO) {
                db.scanDao().getBySessionList(sessionId)
            }
            val result = withContext(Dispatchers.IO) {
                ExcelExporter.export(this@MainActivity, items, sessionId)
            }
            binding.progressBar.visibility = View.GONE

            if (result.success) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("✅ Xuất thành công  (${items.size} dòng)")
                    .setMessage("Lưu tại:\n${result.filePath}\n\nCopy file về máy tính để lọc dữ liệu.")
                    .setPositiveButton("Mở file") { _, _ ->
                        ExcelExporter.openFile(this@MainActivity, result.filePath)
                    }
                    .setNegativeButton("OK", null).show()
            } else {
                Snackbar.make(binding.root, "❌ ${result.error}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    // ── Menu ──────────────────────────────────────────────────────────────
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_history -> {
            startActivity(Intent(this, HistoryActivity::class.java)); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ── Keyence Hardware Scanner: intercept KeyEvent ──────────────────────
    // Máy Keyence gửi chuỗi QR như bàn phím HID, kết thúc = ENTER
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    val raw = scanBuffer.toString().trim()
                    scanBuffer.clear()
                    if (raw.isNotEmpty()) {
                        saveQR(raw)
                        return true
                    }
                }
                else -> {
                    val c = event.unicodeChar.toChar()
                    if (c.code != 0) {
                        scanBuffer.append(c)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
