package com.niconn.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** iOS 系统色板（浅色模式） */
object Apple {
    val blue = Color(0xFF007AFF)
    val red = Color(0xFFFF3B30)
    val green = Color(0xFF34C759)
    val label = Color(0xFF1C1C1E)
    val secondaryLabel = Color(0xFF8E8E93)
    val background = Color(0xFFF2F2F7)
    val surface = Color.White
    val fill = Color(0xFFE5E5EA)
    val separator = Color(0x333C3C43)
    /** 取景页深色胶囊底 */
    val scrim = Color(0x991C1C1E)
    val scrimStrong = Color(0xCC1C1C1E)
    val hairlineOnDark = Color(0x33FFFFFF)
}

/** iOS 导航栏大标题（34pt Bold） */
@Composable
fun AppleLargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Apple.label,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
    )
}

/** iOS 分组列表小节标题（13pt 次要色） */
@Composable
fun AppleSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Apple.secondaryLabel,
        fontSize = 13.sp,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 7.dp),
    )
}

/** iOS 内嵌分组卡片（白底 12dp 圆角） */
@Composable
fun AppleCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    if (onClick != null) {
        Surface(
            shape = shape,
            color = Apple.surface,
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            shape = shape,
            color = Apple.surface,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** iOS 主按钮（填充蓝 50dp 高 14dp 圆角 17pt Semibold） */
@Composable
fun AppleFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Apple.blue,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = Apple.fill,
            disabledContentColor = Apple.secondaryLabel,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** iOS 灰底次级按钮 */
@Composable
fun AppleGrayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Apple.fill,
            contentColor = Apple.label,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * iOS 风格弹窗：白底 14dp 圆角，居中粗体标题 + 内容，底部发丝线分隔的按钮行。
 * destructive=true 时确认按钮为红色加粗（如「删除」）。
 */
@Composable
fun AppleAlert(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    dismissText: String? = null,
    hasBody: Boolean = false,
    body: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            modifier = modifier.width(280.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = Apple.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(
                        start = 24.dp, end = 24.dp,
                        top = 20.dp, bottom = if (hasBody) 12.dp else 16.dp,
                    ),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    body()
                    if (hasBody) Spacer(Modifier.height(16.dp))
                }
                androidx.compose.material3.HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Apple.separator,
                )
                Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (dismissText != null) {
                        AppleAlertButton(
                            text = dismissText,
                            color = Apple.blue,
                            bold = false,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.VerticalDivider(
                            modifier = Modifier.width(0.5.dp).height(48.dp),
                            thickness = 0.5.dp,
                            color = Apple.separator,
                        )
                    }
                    AppleAlertButton(
                        text = confirmText,
                        color = if (destructive) Apple.red else Apple.blue,
                        bold = true,
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleAlertButton(
    text: String,
    color: Color,
    bold: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Transparent,
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = color,
                fontSize = 17.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
