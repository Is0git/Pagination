package com.example.pagination

import androidx.recyclerview.widget.RecyclerView

abstract class PageLoaderAdapter<T, VH : RecyclerView.ViewHolder>(val onBind: (item: T, holder: VH) -> Unit) : RecyclerView.Adapter<VH>() {

    var data: List<T>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun loadPaginationData(value: List<T>?) {
        when {
            value == null || value.isEmpty() -> {
                data = value
                notifyDataSetChanged()
            }
            else -> {
                val begin = data?.count()
                data = value
                notifyItemRangeChanged(begin ?: 0, value.count())
            }
        }
    }

    override fun getItemCount(): Int {
        return data?.count() ?: 0
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        onBind(data?.get(position)!!, holder)
    }
}