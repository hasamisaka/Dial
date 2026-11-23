package com.music.bitchord.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.AccountPlan

/**
 * The plan of an account, as a small pill beside its name: a gold "Premium" with a shine that
 * crosses it now and then, or a grey "Free". Nothing for a plan that is not known.
 */
@Composable
fun PlanBadge(plan: AccountPlan?, modifier: Modifier = Modifier) {
    when (plan) {
        AccountPlan.PREMIUM -> PremiumBadge(modifier)
        AccountPlan.FREE -> Pill(
            text = stringResource(R.string.account_plan_free),
            ink = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
            fill = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        )
        AccountPlan.UNKNOWN, null -> Unit
    }
}

private val PillShape = RoundedCornerShape(50)

private val GoldFill = Brush.linearGradient(listOf(Color(0xFFB8901C), Color(0xFFE2C15A), Color(0xFFC49A22)))
private val GoldInk = Color(0xFF2E2200)

/** One crossing of the shine takes [SHINE_MS]; then it rests until [SHINE_PERIOD_MS]. */
private const val SHINE_PERIOD_MS = 4_500
private const val SHINE_MS = 900

@Composable
private fun PremiumBadge(modifier: Modifier) {
    val shine by rememberInfiniteTransition(label = "premiumShine").animateFloat(
        initialValue = -0.5f,
        targetValue = -0.5f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = SHINE_PERIOD_MS
                -0.5f at 0 using LinearEasing
                -0.5f at SHINE_PERIOD_MS - SHINE_MS using FastOutSlowInEasing
                1.5f at SHINE_PERIOD_MS
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "premiumShineX",
    )
    Pill(
        text = stringResource(R.string.account_plan_premium),
        ink = GoldInk,
        modifier = modifier,
        fill = Modifier
            .background(GoldFill)
            .drawWithContent {
                drawContent()
                // A slanted band of light, from left to right across the pill.
                val band = size.width * 0.35f
                val x = shine * size.width
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent),
                        start = Offset(x - band, 0f),
                        end = Offset(x + band * 0.4f, size.height),
                    ),
                )
            },
    )
}

@Composable
private fun Pill(text: String, ink: Color, modifier: Modifier, fill: Modifier) {
    Box(
        modifier = modifier
            .clip(PillShape)
            .then(fill)
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
