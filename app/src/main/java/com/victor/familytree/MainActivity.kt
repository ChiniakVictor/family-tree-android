package com.victor.familytree

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import com.victor.familytree.ui.FamilyApp
import com.victor.familytree.ui.FamilyTheme
import com.victor.familytree.ui.FamilyViewModel
import com.victor.familytree.ui.StartArgs
import com.victor.familytree.ui.Tab

class MainActivity : ComponentActivity() {
    private val vm: FamilyViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Test hooks: am start -n com.victor.familytree/.MainActivity --es tab tree --es person I0228 --es root I0228 --es query orr
        val start = StartArgs(
            tab = intent.getStringExtra("tab")?.let { t -> Tab.entries.firstOrNull { it.name.equals(t, true) || it.label.equals(t, true) } },
            personId = intent.getStringExtra("person"),
            rootId = intent.getStringExtra("root"),
            query = intent.getStringExtra("query"),
            singlePane = intent.getStringExtra("pane")?.let { it.equals("single", true) },
        )
        setContent {
            FamilyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        FamilyApp(vm, calculateWindowSizeClass(this@MainActivity).widthSizeClass, start)
                    }
                }
            }
        }
    }
}
