package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.theme.CeladonJade
import com.example.ui.theme.CompletedWordBg
import com.example.ui.theme.FixedClueCellBg
import com.example.ui.theme.FixedClueCellBorder
import com.example.ui.theme.IndigoCard
import com.example.ui.theme.IndigoElevated
import com.example.ui.theme.IndigoNight
import com.example.ui.theme.IndigoSurface
import com.example.ui.theme.KyotoGold
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.VermilionRed
import com.example.ui.theme.WashiCream
import com.example.ui.theme.WashiMuted
import com.example.ui.theme.paperGrainOverlay
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
  val existingUser =
    try {
      Firebase.auth.currentUser
    } catch (e: Exception) {
      null
    }
  if (existingUser != null) {
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
        try {
          Firebase.auth.signOut()
        } catch (e: Exception) {
          Log.w("Auth", "Firebase signOut ignored: ${e.message}")
        }
        onSignOutComplete()
      }
    }
  }
}

@Composable
fun OrigamiAuthScreen(
  onAuthSuccess: () -> Unit,
  onContinueAsGuest: () -> Unit = {},
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var isCreateAccountMode by rememberSaveable { mutableStateOf(false) }
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
            colors = listOf(IndigoNight, Color(0xFFEAE1CF), IndigoNight)
          )
        )
        .paperGrainOverlay(alpha = 0.05f, seed = 42)
        .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 460.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = IndigoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier =
          Modifier.fillMaxWidth()
            .border(1.5.dp, IndigoElevated, RoundedCornerShape(24.dp))
            .testTag("origami_login_card"),
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(bottom = 22.dp),
        ) {
          // Top Washi Hero Banner with Origami Tile Emblem
          Box(modifier = Modifier.fillMaxWidth().height(168.dp)) {
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
                      colors =
                        listOf(
                          Color(0x33231E18),
                          IndigoSurface.copy(alpha = 0.96f),
                        )
                    )
                  )
            )
            Column(
              modifier =
                Modifier.align(Alignment.BottomCenter)
                  .padding(horizontal = 18.dp, vertical = 10.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              OrigamiLogoTilesRow()
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "CROSSWORD ORIGAMI",
                style = MaterialTheme.typography.headlineSmall,
                color = WashiCream,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
              )
              Text(
                text = "Handcrafted Washi Word Fill-In & Scrabble Multipliers",
                style = MaterialTheme.typography.bodySmall,
                color = WashiMuted,
                textAlign = TextAlign.Center,
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Sign In vs. Create Account with Gmail Segmented Switcher
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = IndigoCard,
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .border(1.dp, IndigoElevated, RoundedCornerShape(14.dp)),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(4.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              AuthModeTabButton(
                label = "Sign In",
                selected = !isCreateAccountMode,
                onClick = {
                  isCreateAccountMode = false
                  errorMessage = null
                },
                modifier = Modifier.weight(1f).testTag("tab_sign_in_gmail"),
              )
              AuthModeTabButton(
                label = "Create with Gmail",
                selected = isCreateAccountMode,
                onClick = {
                  isCreateAccountMode = true
                  errorMessage = null
                },
                modifier = Modifier.weight(1f).testTag("tab_create_gmail"),
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Contextual Explanation Banner
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isCreateAccountMode) CompletedWordBg else IndigoCard.copy(alpha = 0.65f),
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .border(
                  1.dp,
                  if (isCreateAccountMode) CeladonJade.copy(alpha = 0.45f) else IndigoElevated,
                  RoundedCornerShape(14.dp),
                ),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text =
                  if (isCreateAccountMode) {
                    "Create Your Origami Profile with Gmail"
                  } else {
                    "Welcome Back, Origami Strategist"
                  },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isCreateAccountMode) CeladonJade else SkyCyan,
              )
              Text(
                text =
                  if (isCreateAccountMode) {
                    "Select your @gmail.com account to automatically create your cloud profile with 150 bonus LexiCoins and cross-device puzzle sync."
                  } else {
                    "Sign in with your Gmail account to restore your unlocked puzzle levels, career score, and Washi tile skins."
                  },
                style = MaterialTheme.typography.bodySmall,
                color = WashiMuted,
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          if (errorMessage != null) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = VermilionRed.copy(alpha = 0.12f),
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = 20.dp, vertical = 4.dp)
                  .border(1.dp, VermilionRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            ) {
              Text(
                text = errorMessage!!,
                color = VermilionRed,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          // 1. Primary Gmail / Google Account Button (Sign In or Create Account)
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
                containerColor = if (isCreateAccountMode) CeladonJade else SkyCyan,
                contentColor = Color.White,
              ),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp)
                .testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
              )
            } else {
              Box(
                modifier =
                  Modifier.size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "G",
                  color = if (isCreateAccountMode) CeladonJade else SkyCyan,
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 15.sp,
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text =
                  if (isCreateAccountMode) {
                    stringResource(R.string.create_with_gmail)
                  } else {
                    stringResource(R.string.sign_in_with_google)
                  },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
              )
            }
          }

          // Divider with "OR"
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = IndigoElevated)
            Text(
              text = "OR PLAY OFFLINE",
              style = MaterialTheme.typography.labelSmall,
              color = WashiMuted,
              fontWeight = FontWeight.Bold,
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = IndigoElevated)
          }

          // 2. Secondary "Login as Guest" Button
          OutlinedButton(
            onClick = {
              errorMessage = null
              onContinueAsGuest()
            },
            enabled = !isLoading,
            shape = RoundedCornerShape(14.dp),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = FixedClueCellBg,
                contentColor = WashiCream,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(50.dp)
                .testTag("guest_login_button"),
          ) {
            Icon(
              imageVector = Icons.Default.PersonOutline,
              contentDescription = "Guest login",
              tint = KyotoGold,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.login_as_guest),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.ExtraBold,
              color = WashiCream,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun OrigamiLogoTilesRow() {
  val letters = listOf('C', 'R', 'O', 'S', 'S')
  Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    letters.forEachIndexed { idx, ch ->
      val isCenter = idx == 2
      Box(
        modifier =
          Modifier.size(if (isCenter) 38.dp else 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCenter) CompletedWordBg else FixedClueCellBg)
            .border(
              width = 1.5.dp,
              color = if (isCenter) CeladonJade else FixedClueCellBorder,
              shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val ear = size.width * 0.22f
          val path =
            Path().apply {
              moveTo(size.width - ear, 0f)
              lineTo(size.width, ear)
              lineTo(size.width - ear, ear)
              close()
            }
          drawPath(
            path = path,
            color =
              if (isCenter) CeladonJade.copy(alpha = 0.28f)
              else FixedClueCellBorder.copy(alpha = 0.45f),
          )
        }
        Text(
          text = ch.toString(),
          fontFamily = FontFamily.Serif,
          fontWeight = FontWeight.ExtraBold,
          fontSize = if (isCenter) 18.sp else 16.sp,
          color = WashiCream,
        )
      }
    }
  }
}

@Composable
private fun AuthModeTabButton(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .height(40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) IndigoSurface else Color.Transparent)
        .border(
          width = if (selected) 1.2.dp else 0.dp,
          color = if (selected) IndigoElevated else Color.Transparent,
          shape = RoundedCornerShape(10.dp),
        )
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Default.AccountCircle,
        contentDescription = null,
        tint = if (selected) SkyCyan else WashiMuted,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
        color = if (selected) WashiCream else WashiMuted,
      )
    }
  }
}
