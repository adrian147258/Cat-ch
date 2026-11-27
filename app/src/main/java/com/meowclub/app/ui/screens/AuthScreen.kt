package com.meowclub.app.ui.screens

import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.meowclub.app.data.AppearanceMode
import com.meowclub.app.data.AuthUiState
import com.meowclub.app.ui.components.FloatingCatsBackground
import com.meowclub.app.ui.components.GalleryTextField
import com.meowclub.app.ui.theme.AppFonts
import kotlinx.coroutines.delay

private enum class AuthMode(val label: String) {
    PASSWORD("密码登录"),
    CODE("验证码登录"),
    REGISTER("注册"),
    RESET("重置密码")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    appearance: AppearanceMode,
    authState: AuthUiState,
    onClearFeedback: () -> Unit,
    onSendSignUpCode: (name: String, email: String, password: String) -> Unit,
    onSendSignInCode: (email: String) -> Unit,
    onRegister: (email: String, code: String) -> Unit,
    onLoginPassword: (email: String, password: String) -> Unit,
    onLoginCode: (email: String, code: String) -> Unit,
    onSendResetCode: (email: String) -> Unit,
    onResetPassword: (email: String, code: String, newPassword: String) -> Unit
) {
    val context = LocalContext.current
    val wenKai = remember(context) { AppFonts.wenKai(context) }

    var mode by rememberSaveable { mutableStateOf(AuthMode.PASSWORD) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    LaunchedEffect(authState.codeSentSuccess) {
        if (authState.codeSentSuccess && countdown == 0) {
            countdown = 60
        }
    }

    LaunchedEffect(authState.resetDone) {
        if (authState.resetDone) {
            mode = AuthMode.PASSWORD
            password = ""
            code = ""
        }
    }

    fun isValidEmail(address: String): Boolean {
        val trimmed = address.trim()
        return trimmed.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(trimmed).matches() && trimmed.length <= 254
    }

    fun switchMode(next: AuthMode) {
        mode = next
        localError = ""
        onClearFeedback()
    }

    fun handleSendCode() {
        localError = ""
        val addr = email.trim()
        if (!isValidEmail(addr)) {
            localError = "请填写正确的邮箱"
            return
        }
        when (mode) {
            AuthMode.RESET -> onSendResetCode(addr)
            AuthMode.REGISTER -> {
                if (name.trim().length < 2 || password.length < 10) {
                    localError = "请先填写 2-24 位用户名和至少 10 位密码"
                    return
                }
                onSendSignUpCode(name.trim(), addr, password)
            }
            AuthMode.CODE -> onSendSignInCode(addr)
            else -> {}
        }
    }

    fun handleSubmit() {
        localError = ""
        val addr = email.trim()
        if (!isValidEmail(addr)) {
            localError = "请填写正确的邮箱"
            return
        }
        val otp = code.trim()
        when (mode) {
            AuthMode.PASSWORD -> {
                if (password.isEmpty()) {
                    localError = "请输入密码"
                    return
                }
                onLoginPassword(addr, password)
            }
            AuthMode.CODE -> {
                if (!otp.matches(Regex("[0-9]{6}"))) {
                    localError = "请输入 6 位邮箱验证码"
                    return
                }
                onLoginCode(addr, otp)
            }
            AuthMode.REGISTER -> {
                val trimmedName = name.trim()
                if (trimmedName.length !in 2..24) {
                    localError = "用户名需为 2–24 个字符"
                    return
                }
                if (password.length !in 10..128) {
                    localError = "密码需为 10–128 个字符"
                    return
                }
                if (!otp.matches(Regex("[0-9]{6}"))) {
                    localError = "请输入 6 位邮箱验证码"
                    return
                }
                onRegister(addr, otp)
            }
            AuthMode.RESET -> {
                if (password.length !in 10..128) {
                    localError = "密码需为 10–128 个字符"
                    return
                }
                if (!otp.matches(Regex("[0-9]{6}"))) {
                    localError = "请输入 6 位邮箱验证码"
                    return
                }
                onResetPassword(addr, otp, password)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        FloatingCatsBackground(
            appearance = appearance,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(Modifier.height(16.dp))
        Image(
            painter = rememberAsyncImagePainter("file:///android_asset/splash_cat.png"),
            contentDescription = "Cat-ch",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Cat-ch",
            fontFamily = wenKai,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "捕捉猫咪，也收藏心动",
            fontFamily = wenKai,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(26.dp))

        if (mode != AuthMode.RESET) {
            val primaryModes = listOf(AuthMode.PASSWORD, AuthMode.CODE, AuthMode.REGISTER)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                primaryModes.forEachIndexed { idx, item ->
                    SegmentedButton(
                        selected = mode == item,
                        onClick = { if (!authState.busy) switchMode(item) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = primaryModes.size),
                        enabled = !authState.busy
                    ) {
                        Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "重置账号密码",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = { if (!authState.busy) switchMode(AuthMode.PASSWORD) },
                        enabled = !authState.busy
                    ) {
                        Text("返回登录")
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authForm"
        ) { activeMode ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeMode == AuthMode.REGISTER) {
                    GalleryTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "用户名（2-24 字）",
                        enabled = !authState.busy,
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) }
                    )
                }

                GalleryTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "邮箱地址",
                    enabled = !authState.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) }
                )

                if (activeMode != AuthMode.CODE) {
                    GalleryTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = if (activeMode == AuthMode.REGISTER || activeMode == AuthMode.RESET) "设置密码（至少 10 位）" else "密码",
                        enabled = !authState.busy,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                                )
                            }
                        }
                    )
                }

                if (activeMode != AuthMode.PASSWORD) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GalleryTextField(
                            value = code,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
                            placeholder = "6 位邮箱验证码",
                            enabled = !authState.busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(Icons.Outlined.Pin, contentDescription = null) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        FilledTonalButton(
                            onClick = ::handleSendCode,
                            enabled = !authState.busy && countdown == 0,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text(if (countdown > 0) "${countdown}s" else "发送验证码")
                        }
                    }
                }
            }
        }

        val shownMsg = localError.ifEmpty { authState.message }
        val isMsgError = localError.isNotEmpty() || authState.isError
        if (shownMsg.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMsgError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = shownMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMsgError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = ::handleSubmit,
            enabled = !authState.busy,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (authState.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = when (mode) {
                    AuthMode.REGISTER -> "注册并登录"
                    AuthMode.RESET -> "确认重置密码"
                    else -> "登录"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (mode == AuthMode.PASSWORD) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { if (!authState.busy) switchMode(AuthMode.RESET) },
                enabled = !authState.busy
            ) {
                Text("忘记密码？", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
    }
}
