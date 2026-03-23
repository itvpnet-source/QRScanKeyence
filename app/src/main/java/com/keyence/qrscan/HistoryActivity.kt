package com.keyence.qrscan

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keyence.qrscan.databinding.ActivityHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Lịch sử phiên"

        db = AppDatabase.getInstance(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val sessions = withContext(Dispatchers.IO) { db.scanDao().getAllSessionIds() }

            if (sessions.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                return@launch
            }

            val sdf = SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.getDefault())

            // Hiển thị danh sách phiên đơn giản bằng ArrayAdapter
            val labels = sessions.map { id ->
                val items = withContext(Dispatchers.IO) { db.scanDao().getBySessionList(id) }
                "${sdf.format(Date(id))}   –   ${items.size} lần quét"
            }

            val arrAdapter = android.widget.ArrayAdapter(this@HistoryActivity,
                android.R.layout.simple_list_item_1, labels)

            val listView = android.widget.ListView(this@HistoryActivity)
            listView.adapter = arrAdapter
            listView.setOnItemClickListener { _, _, pos, _ ->
                val sid = sessions[pos]
                AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("Phiên: ${sdf.format(Date(sid))}")
                    .setItems(arrayOf("📥 Xuất Excel", "🗑 Xoá phiên")) { _, which ->
                        if (which == 0) exportSession(sid)
                        else deleteSession(sid)
                    }.show()
            }
            binding.container.addView(listView)
        }
    }

    private fun exportSession(sid: Long) {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { db.scanDao().getBySessionList(sid) }
            val result = withContext(Dispatchers.IO) { ExcelExporter.export(this@HistoryActivity, items, sid) }
            if (result.success) {
                AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("✅ Xuất thành công")
                    .setMessage(result.filePath)
                    .setPositiveButton("Mở") { _, _ -> ExcelExporter.openFile(this@HistoryActivity, result.filePath) }
                    .setNegativeButton("OK", null).show()
            } else {
                Toast.makeText(this@HistoryActivity, result.error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteSession(sid: Long) {
        AlertDialog.Builder(this)
            .setTitle("Xoá phiên này?")
            .setPositiveButton("Xoá") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) { db.scanDao().deleteSession(sid) }
                recreate()
            }
            .setNegativeButton("Huỷ", null).show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
