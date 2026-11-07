package com.autodj.ui.vis

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.autodj.dj.fx.FxType
import kotlin.math.exp
import kotlin.math.min

/**
 * A look drawn entirely on the graphics chip by one AGSL shader: the trip looks (Acid Trip's
 * breathing fractal, Rabbit Hole's tunnel). Android 13 and later only; older phones get the
 * looks' previous scenes instead.
 */
@RequiresApi(33)
class ShaderScene(look: String) : Scene {
    private val shader = RuntimeShader(COMMON + look + MAIN)
    private val brush = ShaderBrush(shader)

    // Every pixel runs the whole shader twice (tracers): far too much at a phone's full resolution.
    override val lowRes = true

    /** The shader's own clock, so the trip holds still while the set is paused. */
    private var clock = 0f
    private var dropAge = 99f
    private var dropCool = 0f
    private var drop = 0f

    override fun step(s: VisualState) {
        if (!s.paused) {
            clock += s.dt
            dropAge += s.dt
        }
        // A drop: the bass back well above where it's been, or an impact the mix plays.
        dropCool -= s.dt
        val hit = s.bass > 0.45f && s.bass - s.slowBass > 0.2f || s.active(FxType.IMPACT) != null || s.active(FxType.SUB_DROP) != null
        if (!s.paused && dropCool <= 0f && hit) {
            dropCool = 12f
            dropAge = 0f
        }
        drop = if (dropAge < 6f) min(1f, dropAge * 3f) * exp(-dropAge * 0.6f) else 0f
    }

    override fun draw(ds: DrawScope, s: VisualState) = with(ds) {
        shader.setFloatUniform("R", size.width, size.height)
        shader.setFloatUniform("T", clock)
        shader.setFloatUniform("K", if (s.paused) 0f else s.kick)
        shader.setFloatUniform("D", drop)
        drawRect(brush)
    }

    companion object {
        /** Noise, flowing noise and the trip palette: deep plum, magenta, amber and teal, drifting slowly. */
        private const val COMMON = """
            uniform float2 R;
            uniform float T;
            uniform float K;
            uniform float D;
            float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
            float noise(float2 p) {
                float2 i = floor(p); float2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
                return mix(mix(hash(i), hash(i + float2(1, 0)), f.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), f.x), f.y);
            }
            float fbm(float2 p) {
                float s = 0.0; float a = 0.5;
                for (int i = 0; i < 4; i++) { s += a * noise(p); p = p * 2.03 + float2(1.7, 9.2); a *= 0.5; }
                return s;
            }
            float3 pal(float t) { return float3(0.5, 0.36, 0.5) + float3(0.5, 0.4, 0.45) * cos(6.2831 * (t + float3(0.0, 0.2, 0.42))); }
        """

        /** Acid Trip: an eight-way mirrored fractal that keeps unfolding from the centre and melts at the edges. */
        const val FRACTAL = """
            float3 look(float2 uv, float t) {
                float r = length(uv); float a = atan(uv.y, uv.x) + t * 0.04;
                float s = 6.2831 / 8.0;
                a = mod(a, s); a = abs(a - s * 0.5);
                float2 p = r * float2(cos(a), sin(a)) * 1.25;
                p += 0.06 * float2(fbm(p * 3.0 + t * 0.3), fbm(p * 3.0 - t * 0.27));
                float2 c = float2(0.80 + 0.05 * sin(t * 0.09) + D * 0.08, 0.56 + 0.05 * cos(t * 0.12));
                float acc = 0.0;
                for (int i = 0; i < 9; i++) { p = abs(p) / dot(p, p) - c; acc += exp(-abs(length(p) - 1.0) * 5.0); }
                float3 col = pal(acc * 0.07 + t * 0.02 + r * 0.25);
                return col * smoothstep(0.0, 1.0, acc * 0.22) * (0.9 + K * 0.2);
            }
        """

        /** Rabbit Hole: a tunnel lined with a living lattice, its walls breathing as you drift down it. */
        const val TUNNEL = """
            float3 look(float2 uv, float t) {
                float r = length(uv); float a = atan(uv.y, uv.x);
                r *= 1.0 + 0.07 * sin(a * 5.0 + t * 1.2) + 0.04 * K;
                float z = 0.32 / max(r, 0.01) + t * (0.55 + D * 1.2);
                float u = a / 6.2831 * 14.0;
                float2 g = float2(u + 0.35 * sin(z * 1.3 + t), z * 2.2);
                g += 0.25 * float2(fbm(g * 0.5 + t * 0.2), fbm(g * 0.5 - t * 0.2));
                float2 gv = fract(g) - 0.5;
                float d = length(gv);
                float ring = smoothstep(0.06, 0.0, abs(d - 0.3)) + 0.6 * smoothstep(0.05, 0.0, abs(d - 0.12 - 0.05 * sin(z + t)));
                float3 col = pal(z * 0.03 + floor(g.y) * 0.025 + t * 0.01) * (0.12 + 0.9 * ring * (0.8 + K * 0.3));
                return col * smoothstep(0.0, 0.55, r) * (1.0 - smoothstep(0.9, 1.6, r) * 0.5);
            }
        """

        /** Breathing, a gentle swim, tracers (fading echoes from a moment ago), a little less than neon, a vignette. */
        private const val MAIN = """
            half4 main(float2 fc) {
                float2 uv = (fc - 0.5 * R) / min(R.x, R.y);
                uv *= 1.0 - 0.045 * sin(T * 0.85) - 0.05 * K;
                uv += (0.008 + D * 0.02) * float2(sin(uv.y * 5.0 + T * 1.1), cos(uv.x * 5.0 + T * 0.9));
                float3 c = look(uv, T) * 0.68 + look(uv * 1.025, T - 0.18) * 0.32;
                c *= 1.0 + D * 0.3;
                float l = dot(c, float3(0.299, 0.587, 0.114));
                c = mix(float3(l), c, 0.88);
                c *= 1.0 - 0.55 * smoothstep(0.45, 1.15, length((fc - 0.5 * R) / R.y));
                return half4(half3(pow(max(c, float3(0.0)), float3(0.95))), 1.0);
            }
        """
    }
}
