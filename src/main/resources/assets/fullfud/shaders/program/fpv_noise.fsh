#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float SignalQuality;

in vec2 texCoord;
out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 uv = texCoord;
    vec2 xy = 2.0 * uv - 1.0;
    float d2 = dot(xy, xy);
    float d = sqrt(d2);
    vec2 distortedUV = 0.5 + (xy * (1.0 + d2 * 0.1445)) * 0.5;

    if (distortedUV.x <= 0.0 || distortedUV.x >= 1.0 || distortedUV.y <= 0.0 || distortedUV.y >= 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec4 col = texture(DiffuseSampler, distortedUV);
    float signalLoss = 1.0 - clamp(SignalQuality, 0.0, 1.0);
    vec2 noiseSeed = floor(uv * InSize);

    float grain = hash12(noiseSeed + vec2(Time * 47.0, Time * 29.0)) - 0.5;
    col.rgb += grain * mix(0.05, 0.08, signalLoss);
    
    if (signalLoss > 0.001) {
        float heavyStatic = hash12(floor(distortedUV * InSize * 0.75) + vec2(Time * 83.0, Time * 61.0));
        vec3 staticColor = vec3(heavyStatic);
        float staticMix = signalLoss * 0.75;
        col.rgb = mix(col.rgb, staticColor, staticMix);
    }

    vec2 screenFragCoord = uv * InSize;
    vec4 mask = vec4(mod(screenFragCoord.x, 3.0) - 1.0, mod(screenFragCoord.x - 1.0, 3.0) - 1.0, mod(screenFragCoord.x - 2.0, 3.0) - 1.0, 1.0);
    
    col *= mask;

    float vignette = 1.0 - smoothstep(0.5, 1.5, d);
    col.rgb *= vignette;

    fragColor = vec4(col.rgb, 1.0);
}
