#version 330
// Mochilas: igual ao shader do Minecraft 26.2, mais o trecho "MOCHILAS" (esconde a mochila do dono na 1a pessoa).

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in float mochilasEsconder;

out vec4 fragColor;

void main() {
    // MOCHILAS: na 1a pessoa a camera fica dentro do dono; a 1,6 bloco ou mais (3a pessoa) aparece.
    if (mochilasEsconder > 0.5 && sphericalVertexDistance < 1.6) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
