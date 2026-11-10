@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.calculator

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CalcTheme { CalculatorScreen() } }
    }
}

// ---------- Theme: Material You on Android 12+, Apple-style orange palette otherwise ----------
@Composable
fun CalcTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
            primary = Color(0xFFFF9F0A), onPrimary = Color.Black,
            primaryContainer = Color(0xFF4A3412), onPrimaryContainer = Color(0xFFFFC46B),
            secondaryContainer = Color(0xFF3A3A3C), onSecondaryContainer = Color(0xFFF2F2F7),
            surfaceContainerHigh = Color(0xFF2C2C2E), background = Color.Black,
            onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFF9A9AA0),
        )
        else -> lightColorScheme(
            primary = Color(0xFFFF9500), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE2B8), onPrimaryContainer = Color(0xFF5A3500),
            secondaryContainer = Color(0xFFE3E3E8), onSecondaryContainer = Color(0xFF1C1C1E),
            surfaceContainerHigh = Color.White, background = Color(0xFFF6F6F8),
            onBackground = Color(0xFF1C1C1E), onSurface = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF6E6E73),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------- Haptics (system API: respects the user's "touch vibration" setting) ----------
private enum class Haptic { TAP, KEY, OK, FAIL, HEAVY }

private fun View.haptic(h: Haptic) {
    val api30 = Build.VERSION.SDK_INT >= 30
    performHapticFeedback(
        when (h) {
            Haptic.TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            Haptic.KEY -> HapticFeedbackConstants.VIRTUAL_KEY
            Haptic.OK -> if (api30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            Haptic.FAIL -> if (api30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            Haptic.HEAVY -> HapticFeedbackConstants.LONG_PRESS
        }
    )
}

// ---------- Screen ----------
private val ROWS = listOf(
    listOf("AC", "()", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("0", ".", "⌫", "="),
)
private val OPS = setOf("÷", "×", "−", "+")
private val FUNCS = setOf("AC", "()", "%", "⌫")

@Composable
fun CalculatorScreen(vm: CalcViewModel = viewModel()) {
    val view = LocalView.current
    var showHistory by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
    ) {
        Display(vm, view, Modifier.weight(if (landscape) 1f else 2f)) { showHistory = true }
        Column(Modifier.weight(if (landscape) 2f else 3f).padding(8.dp)) {
            ROWS.forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { k ->
                        Key(
                            k = k,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onDown = { if (k != "=") view.haptic(if (k in OPS || k in FUNCS) Haptic.KEY else Haptic.TAP) },
                            onTap = {
                                val ok = vm.input(k)
                                if (k == "=") view.haptic(if (ok) Haptic.OK else Haptic.FAIL)
                            },
                            onLong = if (k == "⌫") ({ vm.input("AC"); view.haptic(Haptic.HEAVY) }) else null,
                        )
                    }
                }
            }
        }
    }
    if (showHistory) HistorySheet(vm) { showHistory = false }
}

private fun exprSize(n: Int) = when { n <= 7 -> 68f; n <= 10 -> 56f; n <= 14 -> 46f; n <= 20 -> 38f; else -> 30f }
private fun resultSize(n: Int) = when { n <= 8 -> 72f; n <= 11 -> 58f; n <= 15 -> 46f; else -> 36f }

@Composable
private fun Display(vm: CalcViewModel, view: View, modifier: Modifier, onHistory: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val done = vm.evaluated
    val exprText = Fmt.display(vm.expr).ifEmpty { "0" } + if (done && !vm.isError) " =" else ""
    val resText = if (done) vm.result.orEmpty() else vm.preview

    val eSize by animateFloatAsState(if (done) 30f else exprSize(exprText.length), label = "expr")
    val rSize by animateFloatAsState(if (done) resultSize(resText.length) else 32f, label = "res")

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            // Swipe left/right on the display to delete (Apple style)
            .pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(onDragStart = { acc = 0f }) { _, dx ->
                    acc += dx
                    if (abs(acc) > 70.dp.toPx()) { acc = 0f; if (vm.input("⌫")) view.haptic(Haptic.TAP) }
                }
            }
    ) {
        IconButton(onClick = onHistory) {
            Icon(Icons.Outlined.History, contentDescription = "History", tint = cs.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Text(
            exprText,
            Modifier.fillMaxWidth(),
            color = if (done || vm.expr.isEmpty()) cs.onSurfaceVariant else cs.onSurface,
            fontSize = eSize.sp, lineHeight = (eSize * 1.1f).sp,
            fontWeight = FontWeight.Light, textAlign = TextAlign.End, maxLines = 3,
        )
        Text(
            resText.ifEmpty { " " },
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp).combinedClickable(
                onClick = {},
                onLongClick = {
                    if (resText.isNotEmpty() && !vm.isError) {
                        clipboard.setText(AnnotatedString(resText.replace(",", "").replace('−', '-')))
                        view.haptic(Haptic.HEAVY)
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }
                },
            ),
            color = when { vm.isError -> cs.error; done -> cs.onSurface; else -> cs.onSurfaceVariant },
            fontSize = rSize.sp, lineHeight = (rSize * 1.1f).sp,
            fontWeight = FontWeight.Light, textAlign = TextAlign.End, maxLines = 1,
        )
    }
}

@Composable
private fun Key(k: String, modifier: Modifier, onDown: () -> Unit, onTap: () -> Unit, onLong: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when {
        k == "=" -> cs.primary to cs.onPrimary
        k in OPS -> cs.primaryContainer to cs.onPrimaryContainer
        k in FUNCS -> cs.secondaryContainer to cs.onSecondaryContainer
        else -> cs.surfaceContainerHigh to cs.onSurface
    }
    var pressed by remember { mutableStateOf(false) }
    // Press feedback: key shrinks and its pill shape tightens (Samsung / Apple feel)
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "s")
    val round by animateFloatAsState(if (pressed) 28f else 50f, spring(stiffness = Spring.StiffnessMedium), label = "r")
    val desc = when (k) {
        "⌫" -> "Backspace"; "()" -> "Parentheses"; "÷" -> "Divide"; "×" -> "Multiply"; "−" -> "Minus"
        "+" -> "Plus"; "=" -> "Equals"; "%" -> "Percent"; "." -> "Decimal point"; "AC" -> "All clear"; else -> k
    }

    Box(
        modifier
            .padding(4.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(percent = round.toInt()))
            .background(bg)
            .semantics(mergeDescendants = true) {
                role = Role.Button; contentDescription = desc; onClick { onTap(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; onDown(); tryAwaitRelease(); pressed = false },
                    onTap = { onTap() },
                    onLongPress = onLong?.let { f -> { _ -> f() } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (k == "⌫") Icon(Icons.AutoMirrored.Outlined.Backspace, null, tint = fg, modifier = Modifier.size(30.dp))
        else Text(k, color = fg, fontSize = if (k in OPS || k == "=") 38.sp else 30.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HistorySheet(vm: CalcViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (vm.history.isNotEmpty()) TextButton(onClick = { vm.clearHistory() }) { Text("Clear") }
        }
        if (vm.history.isEmpty()) {
            Text("No calculations yet", Modifier.padding(24.dp), color = cs.onSurfaceVariant)
        }
        LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            items(vm.history) { h ->
                Column(
                    Modifier.fillMaxWidth().clickable { vm.recall(h); onClose() }.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(Fmt.display(h.expr) + " =", color = cs.onSurfaceVariant, fontSize = 16.sp)
                    Text(Fmt.display(h.value), fontSize = 28.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}
