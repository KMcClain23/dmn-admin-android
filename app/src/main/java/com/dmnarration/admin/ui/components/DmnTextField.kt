package com.dmnarration.admin.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.SurfaceBorder
import com.dmnarration.admin.ui.theme.SurfaceRaised

/**
 * A text input in the app's own language rather than Material's.
 *
 * Stock `OutlinedTextField` brings its own palette — a purple focus ring, a
 * floating label in a different typeface, a container that does not match the
 * surfaces around it. On a board built entirely from 8dp cards on `Surface` with
 * an amber accent, it reads as a control borrowed from another app.
 *
 * The 8dp corner is the card radius, `SurfaceRaised` sits a step above the
 * dialog's `Surface` so the field is visibly a field, and the focus ring is the
 * same amber every other active thing on the board uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmnTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    textStyle: TextStyle = DmnType.Body,
    // Carried so the sign-in screen can adopt this without losing its keyboard
    // behaviour. Swapping the control was described as a one-line change; it is not,
    // because dropping visualTransformation would print a password in clear text.
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = DmnTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = textStyle.copy(color = c.textPrimary),
        shape = RoundedCornerShape(8.dp),
        label = label?.let { { Text(it, style = DmnType.Small, color = c.textMuted) } },
        placeholder = placeholder?.let { { Text(it, style = textStyle, color = c.textDim) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accentAmber,
            unfocusedBorderColor = SurfaceBorder,
            disabledBorderColor = SurfaceBorder,
            focusedContainerColor = SurfaceRaised,
            unfocusedContainerColor = SurfaceRaised,
            disabledContainerColor = SurfaceRaised,
            cursorColor = c.accentAmber,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            disabledTextColor = c.textMuted,
            focusedLabelColor = c.accentAmber,
            unfocusedLabelColor = c.textMuted,
        ),
    )
}
