package com.dmnarration.admin.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.DmnTheme

@Composable
fun SignInScreen(
    signingIn: Boolean,
    error: String?,
    onSignIn: (String, String) -> Unit,
) {
    val c = DmnTheme.colors
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.isNotBlank() && !signingIn

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("DMN Admin", style = DmnType.TitleLg, color = c.textPrimary)
        Text(
            "Sign in to see the board.",
            style = DmnType.Small,
            color = c.textMuted,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            enabled = !signingIn,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !signingIn,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSignIn(email, password) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        if (error != null) {
            Text(
                error,
                style = DmnType.Small,
                color = c.alertRed,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Button(
            onClick = { onSignIn(email, password) },
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (signingIn) "Signing in…" else "Sign in")
            }
        }

        if (signingIn) {
            Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accentAmber)
            }
        }

        // No signup link, deliberately: public signup is disabled server-side
        // and there will only ever be a small, hand-created set of users.
    }
}
