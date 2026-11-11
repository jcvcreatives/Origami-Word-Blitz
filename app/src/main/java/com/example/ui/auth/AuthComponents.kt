package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.theme.IndigoCard
import com.example.ui.theme.IndigoNight
import com.example.ui.theme.KyotoGold
import com.example.ui.theme.VermilionRed
import com.example.ui.theme.WashiCream
import com.example.ui.theme.WashiMuted
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope,
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId =
    try {
      context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
      onUnauthenticated()
      return
    }

  val googleIdOption =
    GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(true)
      .build()

  val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

  scope.launch {
    try {
      val result = credentialManager.getCredential(context, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (e: Exception) {
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {},
) {
  val clientId =
    try {
      context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
      onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
      return
    }

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

  scope.launch {
    try {
      val activityContext = context as? Activity ?: context
      val result = credentialManager.getCredential(activityContext, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected credential type")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "Sign in failed")
    }
  }
}

fun signOutUser(
  credentialManager: CredentialManager,
  scope: CoroutineScope,
  onSignOutComplete: () -> Unit = {},
) {
  scope.launch {
    withContext(NonCancellable) {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w("Auth", "Failed to clear credential state: ${e.message}")
      } finally {
        Firebase.auth.signOut()
        onSignOutComplete()
      }
    }
  }
}

@Composable
fun OrigamiAuthScreen(onAuthSuccess: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(IndigoNight, Color(0xFF1A2242), IndigoNight)
          )
        )
        .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp).padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = IndigoCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(bottom = 24.dp),
        ) {
          Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
            Image(
              painter = painterResource(id = R.drawable.img_origami_hero_1794379928974),
              contentDescription = stringResource(R.string.cd_hero_banner),
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier =
                Modifier.fillMaxSize()
                  .background(
                    Brush.verticalGradient(
                      colors = listOf(Color.Transparent, IndigoCard.copy(alpha = 0.95f))
                    )
                  )
            )
            Column(
              modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            ) {
              Text(
                text = "ORIGAMI: WORD BLITZ",
                style = MaterialTheme.typography.headlineMedium,
                color = KyotoGold,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Tactical 15×15 Crossword • Rogue Relics • 120 FPS Washi Craft",
                style = MaterialTheme.typography.bodyMedium,
                color = WashiCream,
              )
            }
          }

          Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            FeatureHighlightRow(
              icon = Icons.Default.GridOn,
              title = "15×15 Crossword Multipliers",
              subtitle = "Pinch-to-zoom canvas board with 2L, 3L, 2W, and 3W explosive bursts.",
            )
            FeatureHighlightRow(
              icon = Icons.Default.Bolt,
              title = "Sub-2ms Trie Lexicon & AI Scribes",
              subtitle = "Battle 4 themed AI Origami Masters or challenge friends in Pass & Play.",
            )
            FeatureHighlightRow(
              icon = Icons.Default.AutoAwesome,
              title = "Cloud Progression & Mythic Tile Skins",
              subtitle = "Sync your campaign stage, high scores, and cosmetic tiles across devices.",
            )
          }

          if (errorMessage != null) {
            Text(
              text = errorMessage!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Button(
            onClick = {
              errorMessage = null
              isLoading = true
              onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = { isLoading = false },
              )
            },
            enabled = !isLoading,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = VermilionRed,
                contentColor = Color.White,
              ),
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(54.dp)
                .testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
              )
            } else {
              Text(
                text = stringResource(R.string.sign_in_with_google),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FeatureHighlightRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(KyotoGold.copy(alpha = 0.16f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = KyotoGold,
        modifier = Modifier.size(22.dp),
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = WashiCream,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = WashiMuted,
      )
    }
  }
}
