package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.OrigamiCloudRepository
import com.example.ui.auth.OrigamiAuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.feedback.SoundAndHapticsEngine
import com.example.ui.screens.OrigamiMainScreen
import com.example.ui.theme.OrigamiWordBlitzTheme
import com.example.ui.viewmodel.GameViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      OrigamiWordBlitzTheme {
        OrigamiAppRoot()
      }
    }
  }
}

@Composable
fun OrigamiAppRoot(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by
    auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    OrigamiAuthScreen(onAuthSuccess = {})
  } else {
    val uid = user.uid
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    key(uid) {
      val gameViewModel: GameViewModel =
        viewModel(
          key = uid,
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = OrigamiCloudRepository(db)
                val soundEngine = SoundAndHapticsEngine(app)
                GameViewModel(
                  repository = repo,
                  currentUserId = uid,
                  soundEngine = soundEngine,
                )
              }
            },
        )

      OrigamiMainScreen(
        viewModel = gameViewModel,
        onSignOut = {
          signOutUser(
            credentialManager = credentialManager,
            scope = scope,
          )
        },
      )
    }
  }
}
