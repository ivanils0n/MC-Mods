#version 330
// Mochilas: igual ao shader do Minecraft 26.2, mais o trecho "MOCHILAS" (esconde a mochila do dono na 1a pessoa).

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#moj_import <minecraft:light.glsl>
#endif
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

#ifndef NO_OVERLAY
uniform sampler2D Sampler1;
#endif

#ifndef EMISSIVE
uniform sampler2D Sampler2;
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
out vec4 vertexPerFaceColorBack;
out vec4 vertexPerFaceColorFront;
#else
out vec4 vertexColor;
#endif

#ifndef EMISSIVE
out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
out vec4 overlayColor;
#endif

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

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, Normal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, mochilasCor);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, mochilasCor);
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = mochilasCor;
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, mochilasCor);
#endif

#ifndef EMISSIVE
    lightMapColor = sample_lightmap(Sampler2, UV2);
#endif

#ifndef NO_OVERLAY
    overlayColor = texelFetch(Sampler1, UV1, 0);
#endif

    texCoord0 = UV0;

#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
