# MC-Mods

Mods Fabric para Minecraft. Cada pasta `<mod>-<versão>/` é um mod independente.

| Mod | Pasta | Minecraft |
|-----|-------|-----------|
| Mob Health Bar | `mobhealthbar-1.0.0/` | 26.2 e 26.3+ (Fabric, só cliente) |

## Compilar

O GitHub Actions compila a cada push. Os `.jar` ficam em **Actions → último build → Artifacts** (um `mods-mc<versão>` por versão do Minecraft, definidas na matriz do `build.yml`).

Localmente (Java 25): `./gradlew build` → `<pasta>/build/libs/`.

## Novo mod

Crie uma pasta `<mod>-<versão>/` com `build.gradle` e `gradle.properties` (copie do mobhealthbar). Ela entra no build automaticamente.
