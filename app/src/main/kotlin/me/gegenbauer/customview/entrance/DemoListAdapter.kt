package me.gegenbauer.customview.entrance

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.gegenbauer.customview.R

class DemoListAdapter: ListAdapter<Demo, DemoListAdapter.DemoItemViewHolder>(DemoItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DemoItemViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.demo_item, parent, false)
        return DemoItemViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DemoItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DemoItemViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.name)

        fun bind(demo: Demo) {
            name.text = demo.name
            itemView.setOnClickListener {
                it.context.startActivity(Intent(it.context, Class.forName(demo.clazz)))
            }
        }
    }

    class DemoItemDiffCallback: DiffUtil.ItemCallback<Demo>() {
        override fun areItemsTheSame(oldItem: Demo, newItem: Demo): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Demo, newItem: Demo): Boolean {
            return oldItem == newItem
        }
    }
}