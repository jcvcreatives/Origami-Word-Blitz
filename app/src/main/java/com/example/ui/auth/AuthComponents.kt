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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class LocalOrigamiAccount(
  val username: String,
  val email: String,
)

object LocalAccountStore {
  private const val PREFS_NAME = "origami_accounts_prefs"
  private const val KEY_ACTIVE_USERNAME = "active_username"
  private const val KEY_ACTIVE_EMAIL = "active_email"

  private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun getActiveAccount(context: Context): LocalOrigamiAccount? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val username = prefs.getString(KEY_ACTIVE_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
    val email = prefs.getString(KEY_ACTIVE_EMAIL, "") ?: ""
    return LocalOrigamiAccount(username = username, email = email)
  }

  fun registerAccount(
    context: Context,
    username: String,
    passwordPlain: String,
    email: String = "",
  ): Result<LocalOrigamiAccount> {
    val cleanUser = username.trim()
    if (cleanUser.length < 2) {
      return Result.failure(IllegalArgumentException("Please enter a username (at least 2 characters)."))
    }
    if (passwordPlain.length < 4) {
      return Result.failure(IllegalArgumentException("Password must be at least 4 characters."))
    }

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val passHash = sha256(passwordPlain)
    val userKey = cleanUser.lowercase()
    val cleanEmail = email.trim().lowercase().ifBlank { "$userKey@origami.local" }

    prefs
      .edit()
      .putString("acct_user_$userKey", cleanUser)
      .putString("acct_email_$userKey", cleanEmail)
      .putString("acct_pass_$userKey", passHash)
      .putString("acct_user_by_email_$cleanEmail", userKey)
      .putString(KEY_ACTIVE_USERNAME, cleanUser)
      .putString(KEY_ACTIVE_EMAIL, cleanEmail)
      .apply()

    return Result.success(LocalOrigamiAccount(username = cleanUser, email = cleanEmail))
  }

  fun loginAccount(
    context: Context,
    identifier: String,
    passwordPlain: String,
  ): Result<LocalOrigamiAccount> {
    val cleanId = identifier.trim()
    if (cleanId.isBlank()) {
      return Result.failure(IllegalArgumentException("Please enter your username."))
    }
    if (passwordPlain.isBlank()) {
      return Result.failure(IllegalArgumentException("Please enter your password."))
    }

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val lookupKey =
      if (cleanId.contains("@")) {
        prefs.getString("acct_user_by_email_${cleanId.lowercase()}", cleanId.lowercase())
          ?: cleanId.lowercase()
      } else {
        cleanId.lowercase()
      }

    val storedPassHash = prefs.getString("acct_pass_$lookupKey", null)
    val inputHash = sha256(passwordPlain)

    if (storedPassHash != null && storedPassHash != inputHash) {
      return Result.failure(IllegalArgumentException("Incorrect password for '$cleanId'."))
    }

    val resolvedUsername =
      prefs.getString("acct_user_$lookupKey", null)
        ?: cleanId.substringBefore("@").ifBlank { "Origami Player" }
    val resolvedEmail =
      prefs.getString("acct_email_$lookupKey", null)
        ?: if (cleanId.contains("@")) cleanId.lowercase() else "$lookupKey@origami.local"

    // Persist if first-time login so subsequent logins remember credentials
    if (storedPassHash == null) {
      prefs
        .edit()
        .putString("acct_user_$lookupKey", resolvedUsername)
        .putString("acct_email_$lookupKey", resolvedEmail)
        .putString("acct_pass_$lookupKey", inputHash)
        .putString("acct_user_by_email_$resolvedEmail", lookupKey)
        .apply()
    }

    prefs
      .edit()
      .putString(KEY_ACTIVE_USERNAME, resolvedUsername)
      .putString(KEY_ACTIVE_EMAIL, resolvedEmail)
      .apply()

    return Result.success(LocalOrigamiAccount(username = resolvedUsername, email = resolvedEmail))
  }

  fun clearActiveAccount(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_ACTIVE_USERNAME).remove(KEY_ACTIVE_EMAIL).apply()
  }
}

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
  onLocalAccountAuthenticated: (LocalOrigamiAccount) -> Unit = {},
  onContinueAsGuest: () -> Unit = {},
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }

  // Main flow defaults to "Create Account" (true); user can switch to "Log In" (false)
  var isCreateAccountMode by rememberSaveable { mutableStateOf(true) }
  var usernameInput by rememberSaveable { mutableStateOf("") }
  var passwordInput by rememberSaveable { mutableStateOf("") }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }
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
          .padding(horizontal = 20.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
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
          modifier = Modifier.padding(bottom = 20.dp),
        ) {
          // Top Handcrafted Washi Paper Header (Clean Geometric Tiles — No AI Artwork)
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .background(
                  Brush.verticalGradient(
                    colors =
                      listOf(
                        Color(0xFFEDE4D2),
                        IndigoSurface,
                      )
                  )
                )
                .padding(horizontal = 18.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              OrigamiLogoTilesRow()
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "CROSSWORD ORIGAMI",
                style = MaterialTheme.typography.headlineSmall,
                color = WashiCream,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Primary Mode Switcher: Create Account (Main) | Log In
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
                label = "Create Account",
                selected = isCreateAccountMode,
                onClick = {
                  isCreateAccountMode = true
                  errorMessage = null
                },
                modifier = Modifier.weight(1f).testTag("tab_create_account"),
              )
              AuthModeTabButton(
                label = "Log In",
                selected = !isCreateAccountMode,
                onClick = {
                  isCreateAccountMode = false
                  errorMessage = null
                },
                modifier = Modifier.weight(1f).testTag("tab_sign_in"),
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Main Account Creation / Login Form Fields (Username & Password only)
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedTextField(
              value = usernameInput,
              onValueChange = {
                usernameInput = it
                errorMessage = null
              },
              label = { Text("Username") },
              placeholder = { Text("e.g. OrigamiMaster") },
              leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null, tint = SkyCyan)
              },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("input_account_username"),
            )

            OutlinedTextField(
              value = passwordInput,
              onValueChange = {
                passwordInput = it
                errorMessage = null
              },
              label = { Text("Password") },
              placeholder = { Text("Enter password") },
              leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = SkyCyan)
              },
              trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                  Icon(
                    imageVector =
                      if (passwordVisible) Icons.Default.VisibilityOff
                      else Icons.Default.Visibility,
                    contentDescription =
                      if (passwordVisible) "Hide password" else "Show password",
                    tint = WashiMuted,
                  )
                }
              },
              visualTransformation =
                if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("input_account_password"),
            )
          }

          if (errorMessage != null) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = VermilionRed.copy(alpha = 0.12f),
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = 20.dp, vertical = 6.dp)
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

          Spacer(modifier = Modifier.height(12.dp))

          // 1. MAIN CTA: Create Account / Log In
          Button(
            onClick = {
              errorMessage = null
              val result =
                if (isCreateAccountMode) {
                  LocalAccountStore.registerAccount(
                    context = context,
                    username = usernameInput,
                    passwordPlain = passwordInput,
                  )
                } else {
                  LocalAccountStore.loginAccount(
                    context = context,
                    identifier = usernameInput,
                    passwordPlain = passwordInput,
                  )
                }
              result
                .onSuccess { account -> onLocalAccountAuthenticated(account) }
                .onFailure { err -> errorMessage = err.message ?: "Authentication failed" }
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
                .height(50.dp)
                .testTag("primary_account_submit_button"),
          ) {
            Text(
              text =
                if (isCreateAccountMode) {
                  "${stringResource(R.string.create_account_primary)} (+150 Coins)"
                } else {
                  stringResource(R.string.log_in_primary)
                },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.ExtraBold,
            )
          }

          // Divider for Optional Sign-In Methods
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = IndigoElevated)
            Text(
              text = "OPTIONAL QUICK ACCESS",
              style = MaterialTheme.typography.labelSmall,
              color = WashiMuted,
              fontWeight = FontWeight.Bold,
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = IndigoElevated)
          }

          // 2. OPTIONAL: Continue with Gmail (Google)
          OutlinedButton(
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
            shape = RoundedCornerShape(14.dp),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = IndigoCard.copy(alpha = 0.55f),
                contentColor = WashiCream,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(48.dp)
                .testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = SkyCyan,
                strokeWidth = 2.2.dp,
              )
            } else {
              Box(
                modifier =
                  Modifier.size(22.dp)
                    .clip(CircleShape)
                    .background(SkyCyan),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "G",
                  color = Color.White,
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 13.sp,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.sign_in_with_google),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = WashiCream,
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // 3. OPTIONAL: Login as Guest
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
                .height(48.dp)
                .testTag("guest_login_button"),
          ) {
            Icon(
              imageVector = Icons.Default.PersonOutline,
              contentDescription = "Guest login",
              tint = KyotoGold,
              modifier = Modifier.size(19.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.login_as_guest),
              style = MaterialTheme.typography.labelLarge,
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
