#version 330
// Mochilas: igual ao shader do Minecraft 26.2, mais o trecho "MOCHILAS" (esconde a mochila do dono na 1a pessoa).

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;

out vec2 texCoord0;
out float mochilasEsconder;
// MOCHILAS: a copia da mochila que so o dono recebe vem com a cor-marca (254, 253, 252).
bool mochilas_marca(vec4 c) {
    return all(lessThan(abs(c.rgb - vec3(254.0, 253.0, 252.0) / 255.0), vec3(0.0015)));
}


void main() {
    // MOCHILAS
    bool mochilasMarca = mochilas_marca(Color);
    vec4 mochilasCor = mochilasMarca ? vec4(1.0, 1.0, 1.0, Color.a) : Color;
    mochilasEsconder = mochilasMarca ? 1.0 : 0.0;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, mochilasCor);
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);

    texCoord0 = UV0;
}
