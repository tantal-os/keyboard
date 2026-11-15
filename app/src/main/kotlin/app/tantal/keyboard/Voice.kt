package app.tantal.keyboard

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.sin

// p runs from -1 to 1 across the shorter side; colors come premultiplied out.
private const val SHADER_HEAD = """
uniform float2 size;
uniform float time;
uniform float level;
layout(color) uniform half4 c1;
layout(color) uniform half4 c2;
layout(color) uniform half4 c3;

float2 at(float2 fc) { return (fc - size * 0.5) / (min(size.x, size.y) * 0.5); }

float pixel() { return 2.0 / min(size.x, size.y); }
"""

/** Two liquid outlines whose edges wobble harder when the voice is louder. */
private const val LIQUID = SHADER_HEAD + """
half4 main(float2 fc) {
    float2 p = at(fc);
    float t = time;
    float ang = atan(p.y, p.x);
    float d = length(p);
    float aa = 1.5 * pixel();
    float r1 = 0.6 + 0.04 * sin(3.0 * ang + t * 1.4) + (0.03 + 0.08 * level) * sin(5.0 * ang - t * 1.1);
    float r2 = 0.8 + 0.04 * sin(4.0 * ang - t * 0.9) + (0.02 + 0.07 * level) * sin(7.0 * ang + t * 1.7);
    float m1 = 1.0 - smoothstep(r1 - aa, r1 + aa, d);
    float m2 = (1.0 - smoothstep(r2 - aa, r2 + aa, d)) * 0.7;
    // A linear gradient turning slowly; one around the angle would pinch into a point in the middle.
    float2 dir = float2(cos(t * 0.6), sin(t * 0.6));
    half3 front = mix(c1.rgb, c2.rgb, smoothstep(-0.7, 0.7, dot(p, dir)));
    half4 back = half4(c3.rgb * m2, m2);
    return half4(front * m1, m1) + back * (1.0 - m1);
}
"""

/** A made-up voice level, 0 to 1, until there is a real microphone. */
private fun level(t: Float) = (0.5f + 0.5f * sin(t * 3.1f) * sin(t * 1.7f + 1f)).coerceIn(0f, 1f)

@Composable
fun Voice(board: Board, ime: TantalIme, haptic: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }
    val shader = remember { RuntimeShader(LIQUID) }
    val brush = remember { ShaderBrush(shader) }
    val stop = stringResource(R.string.voice_stop)
    Column(
        Modifier.fillMaxWidth().height(panelHeight(board.prefs)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.voice_listening),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .size(176.dp)
                .clip(CircleShape)
                .semantics { contentDescription = stop }
                .clickable(onClickLabel = stop) {
                    haptic()
                    ime.showKeys()
                },
        ) {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("level", level(time))
            shader.setColorUniform("c1", colors.primary.toArgb())
            shader.setColorUniform("c2", colors.tertiary.toArgb())
            shader.setColorUniform("c3", colors.primaryContainer.toArgb())
            drawRect(brush)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.voice_mock),
            Modifier.padding(horizontal = 32.dp),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
