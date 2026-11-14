package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.OrigamiCloudRepository
import com.example.ui.auth.LocalAccountStore
import com.example.ui.auth.LocalOrigamiAccount
import com.example.ui.auth.OrigamiAuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.feedback.SoundAndHapticsEngine
import com.example.ui.screens.LexiFillMainScreen
import com.example.ui.theme.LexiFillTheme
import com.example.ui.viewmodel.GameViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

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
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  var currentUser by remember {
    mutableStateOf<FirebaseUser?>(
      try {
        Firebase.auth.currentUser
      } catch (e: Exception) {
        null
      }
    )
  }
  var localAccount by remember {
    mutableStateOf<LocalOrigamiAccount?>(LocalAccountStore.getActiveAccount(context))
  }
  var isGuestMode by rememberSaveable { mutableStateOf(false) }

  DisposableEffect(Unit) {
    val authInstance =
      try {
        Firebase.auth
      } catch (e: Exception) {
        null
      }
    val listener =
      FirebaseAuth.AuthStateListener { auth ->
        currentUser = auth.currentUser
        if (auth.currentUser != null) {
          isGuestMode = false
        }
      }
    authInstance?.addAuthStateListener(listener)
    onDispose {
      authInstance?.removeAuthStateListener(listener)
    }
  }

  val activeFirebaseUser = currentUser
  val activeLocalAccount = localAccount

  if (activeFirebaseUser == null && activeLocalAccount == null && !isGuestMode) {
    OrigamiAuthScreen(
      onAuthSuccess = {
        currentUser =
          try {
            Firebase.auth.currentUser
          } catch (e: Exception) {
            null
          }
        isGuestMode = false
      },
      onLocalAccountAuthenticated = { account ->
        localAccount = account
        isGuestMode = false
      },
      onContinueAsGuest = {
        isGuestMode = true
      },
    )
  } else if (activeFirebaseUser != null) {
    val uid = activeFirebaseUser.uid
    val preferredName =
      activeLocalAccount?.username?.takeIf { it.isNotBlank() }
        ?: activeFirebaseUser.displayName?.takeIf { it.isNotBlank() }
        ?: activeFirebaseUser.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: "Origami Strategist"
    val emailLabel =
      if (activeLocalAccount != null) {
        "${activeLocalAccount.username} · ${activeFirebaseUser.email ?: activeLocalAccount.email}"
      } else {
        activeFirebaseUser.email?.takeIf { it.isNotBlank() }
          ?: activeFirebaseUser.displayName?.takeIf { it.isNotBlank() }
          ?: preferredName
      }
    DisposableEffect(uid, preferredName) {
      localAccount =
        LocalAccountStore.recordFirebaseRegistration(
          context = context,
          username = preferredName,
          email = activeFirebaseUser.email ?: activeLocalAccount?.email.orEmpty(),
          firebaseUid = uid,
        )
      onDispose {}
    }
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
                val soundEngine = SoundAndHapticsEngine(app)
                GameViewModel(
                  repository = OrigamiCloudRepository(app),
                  currentUserId = uid,
                  preferredDisplayName = preferredName,
                  soundEngine = soundEngine,
                )
              }
            },
        )

      LexiFillMainScreen(
        viewModel = gameViewModel,
        userEmail = emailLabel,
        isGuestUser = false,
        onSignOut = {
          LocalAccountStore.clearActiveAccount(context)
          localAccount = null
          signOutUser(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = {
              currentUser = null
              isGuestMode = false
            },
          )
        },
      )
    }
  } else if (activeLocalAccount != null) {
    val accountKey = "local_${activeLocalAccount.username.lowercase()}"
    key(accountKey) {
      val gameViewModel: GameViewModel =
        viewModel(
          key = accountKey,
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                val soundEngine = SoundAndHapticsEngine(app)
                GameViewModel(
                  repository = null,
                  currentUserId = null,
                  soundEngine = soundEngine,
                )
              }
            },
        )

      LexiFillMainScreen(
        viewModel = gameViewModel,
        userEmail = "${activeLocalAccount.username} (${activeLocalAccount.email})",
        isGuestUser = false,
        onSignOut = {
          LocalAccountStore.clearActiveAccount(context)
          localAccount = null
          isGuestMode = false
        },
      )
    }
  } else {
    // Guest Mode: 100% local & offline-first (never queries Firestore while currentUser == null)
    key("guest_session") {
      val gameViewModel: GameViewModel =
        viewModel(
          key = "guest_session",
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                val soundEngine = SoundAndHapticsEngine(app)
                GameViewModel(
                  repository = null,
                  currentUserId = null,
                  soundEngine = soundEngine,
                )
              }
            },
        )

      LexiFillMainScreen(
        viewModel = gameViewModel,
        userEmail = "Guest Folding Master",
        isGuestUser = true,
        onSignOut = {
          isGuestMode = false
        },
      )
    }
  }
}
