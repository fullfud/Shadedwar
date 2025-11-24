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
    
    // Noise strength depends on signal quality (1.0 = good, 0.0 = bad)
    float noiseFactor = clamp(1.0 - SignalQuality, 0.0, 1.0);
    
    // Glitch / Tear Effect
    if (mod(Time, 2.0) > 1.9) {
        uv.x += (cos(Time * 10.0 + uv.y * 1000.0) * 0.01) * noiseFactor;
    }
    
    if (mod(Time, 5.0) > 3.75) {
        vec2 noiseShift = 1.0 / 64.0 * (2.0 * vec2(rand(floor(uv * 32.0) + vec2(32.05, 236.0)), rand(floor(uv.y * 32.0) + vec2(-62.05, -36.0))) - 1.0);
        uv += noiseShift * noiseFactor;
    }

    // Fisheye Lens Effect
    float PI = 3.1415926535;
    float aperture = 178.0;
    float apertureHalf = 0.5 * aperture * (PI / 180.0);
    float maxFactor = sin(apertureHalf);
    
    vec2 xy = (2.0 * uv - 1.0) * 0.7;
    float d = length(xy);
    
    vec2 finalUV = uv;
    
    if (d < (2.0 - maxFactor)) {
        d = length(xy * maxFactor);
        float z = sqrt(1.0 - d * d);
        float r = atan(d, z) / PI;
        float phi = atan(xy.y, xy.x);
        finalUV.x = r * cos(phi) + 0.5;
        finalUV.y = r * sin(phi) + 0.5;
    }
    
    vec4 col = texture(DiffuseSampler, finalUV);
    
    // Static / Snow Noise
    float r = rand(vec2(Time, gl_FragCoord.y + gl_FragCoord.x)) - 0.5;
    float g = rand(vec2(Time + 0.1, gl_FragCoord.y + gl_FragCoord.x)) - 0.5;
    float b = rand(vec2(Time + 0.2, gl_FragCoord.y + gl_FragCoord.x)) - 0.5;
    
    col.rgb += vec3(r, g, b) * 0.6 * noiseFactor;
    
    // If signal is very weak, mix to heavy static
    if (noiseFactor > 0.8) {
        float heavyStatic = rand(vec2(Time * 5.0, gl_FragCoord.x * gl_FragCoord.y));
        col.rgb = mix(col.rgb, vec3(heavyStatic), (noiseFactor - 0.8) * 5.0);
    }
    
    fragColor = col;
}