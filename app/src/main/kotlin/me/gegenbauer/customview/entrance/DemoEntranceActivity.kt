package me.gegenbauer.customview.entrance

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import me.gegenbauer.customview.R

class DemoEntranceActivity: AppCompatActivity() {

    private val demoListRv: RecyclerView by lazy {
        findViewById(R.id.demo_list)
    }
    private val demoListAdapter = DemoListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo_entrance)
        demoListRv.adapter = demoListAdapter
        demoListRv.layoutManager = LinearLayoutManager(this)
        lifecycleScope.launch {
            ManifestDemoCollector(this@DemoEntranceActivity).collect().let {
                demoListAdapter.submitList(it)
            }
        }
    }
}