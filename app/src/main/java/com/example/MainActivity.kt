package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ui.feedback.SoundAndHapticsEngine
import com.example.ui.screens.LexiFillMainScreen
import com.example.ui.theme.LexiFillTheme
import com.example.ui.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      LexiFillTheme {
        LexiFillAppRoot()
      }
    }
  }
}

@Composable
fun LexiFillAppRoot() {
  val gameViewModel: GameViewModel =
    viewModel(
      factory =
        viewModelFactory {
          initializer {
            val app =
              checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
            val soundEngine = SoundAndHapticsEngine(app)
            // Offline-first & zero network quota footprint
            GameViewModel(
              repository = null,
              currentUserId = null,
              soundEngine = soundEngine,
            )
          }
        }
    )

  LexiFillMainScreen(
    viewModel = gameViewModel,
  )
}
