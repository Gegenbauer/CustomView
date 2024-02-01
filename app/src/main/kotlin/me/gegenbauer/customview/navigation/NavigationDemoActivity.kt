package me.gegenbauer.customview.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.gegenbauer.customview.R

class NavigationDemoActivity : AppCompatActivity() {

    private val navigationView: CarNavigationView by lazy {
        findViewById(R.id.car_navigation_view)!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation_demo)
        lifecycleScope.launch {
            progressGrowToComplete()
            setNearby()
            setFaraway()
            setNearby()
        }
    }

    private suspend fun progressGrowToComplete() {
        var progress = 6000
        while(progress < 10000) {
            navigationView.smoothlySetProgress(progress / 10000f)
            progress += 5
            delay(5)
        }
        navigationView.smoothlySetProgress(1f)
        delay(5000)
    }

    private suspend fun setNearby() {
        navigationView.setNearbyState(true)
        delay(5000)
    }

    private suspend fun setFaraway() {
        navigationView.smoothlySetProgress(0.7f)
        delay(2000)
    }
}