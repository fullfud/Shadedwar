#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float SignalQuality;

in vec2 texCoord;
out vec4 fragColor;

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;

    float dummy = InSize.x + InSize.y;
    if (dummy <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec2 xy = 2.0 * uv - 1.0;
    float d = length(xy);
    float r = d * 0.85;
    vec2 distortedUV = 0.5 + (xy * (1.0 + r * r * 0.2)) * 0.5;

    if (distortedUV.x <= 0.0 || distortedUV.x >= 1.0 || distortedUV.y <= 0.0 || distortedUV.y >= 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec4 col = texture(DiffuseSampler, distortedUV);

    float noiseFactor = clamp(1.0 - SignalQuality, 0.0, 1.0);
    float grain = rand(vec2(Time * 50.0, uv.y * 100.0 + uv.x)) - 0.5;
    col.rgb += grain * 0.08;

    if (noiseFactor > 0.05) {
        float staticNoise = rand(vec2(Time, gl_FragCoord.y * gl_FragCoord.x));
        col.rgb = mix(col.rgb, vec3(staticNoise), noiseFactor * 0.9);

        if (noiseFactor > 0.3 && mod(Time, 1.0) > 0.8) {
            col.rgb += 0.2;
        }
    }

    float vignette = 1.0 - smoothstep(0.5, 1.5, d);
    col.rgb *= vignette;

    fragColor = vec4(col.rgb, 1.0);
}
