#version 150 core

uniform sampler2D depth;
uniform sampler2D altDepth;
uniform float lastFrameTime;
uniform float decay;

out float iris_fragColor;

// Visus V-02 (golden/CHANGES.md): a NaN or Inf in the current depth, the previous smoothed depth or the decay must not
// latch into centerDepthSmooth. Finite inputs give the same result as Oculus 1.8.0.
void main() {
    float currentDepth = texture(depth, vec2(0.5)).r;
    float oldDepth = texture(altDepth, vec2(0.5)).r;

    if (isnan(oldDepth) || isinf(oldDepth)) {
       oldDepth = currentDepth;
    }

    if (isnan(currentDepth) || isinf(currentDepth)) {
       currentDepth = oldDepth;
    }

    // decay is +Inf when the pack sets centerDepthHalflife to 0; -Inf * 0 would be NaN on a frame with no time step.
    float decay2 = isinf(decay) ? 1.0 : 1.0 - exp(-decay * max(lastFrameTime, 0.0));

    if (isnan(decay2)) {
       decay2 = 1.0;
    }

    iris_fragColor = mix(oldDepth, currentDepth, clamp(decay2, 0.0, 1.0));
}
