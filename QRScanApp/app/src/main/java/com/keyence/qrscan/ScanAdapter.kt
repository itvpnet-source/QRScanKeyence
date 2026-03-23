package com.keyence.qrscan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.keyence.qrscan.databinding.ItemScanBinding

class ScanAdapter(
    private val onDeleteClick: (ScanItem) -> Unit
) : ListAdapter<ScanItem, ScanAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemScanBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScanItem, position: Int) {
            binding.tvNo.text       = "${position + 1}"
            binding.tvRawQr.text    = item.rawQr
            binding.tvDateTime.text = item.getFormattedDateTime()
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ScanItem>() {
            override fun areItemsTheSame(a: ScanItem, b: ScanItem) = a.id == b.id
            override fun areContentsTheSame(a: ScanItem, b: ScanItem) = a == b
        }
    }
}
