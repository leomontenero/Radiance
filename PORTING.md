# Porting Radiance to Minecraft 26.1.2

Tracking branch: `port/26.1.2`. Source baseline: `main` @ MC `1.21.4` / Java 21 / yarn mappings.

## Why this is non-trivial

- MC `26.1` is the **first unobfuscated** Minecraft. Yarn/intermediary support stops at `1.21.11` and is removed for `26.1+`.
- Mod source currently uses yarn names (`MinecraftClient`, `NativeImage`, `RenderSystem`, etc.). Every reference must move to Mojang's official name (different package, different class/method names).
- Fabric Loom plugin id and behavior changed; build script needs full rework.
- **MCVR (native side) has no 26.1 fork yet** — once Java compiles and loads, runtime will crash because vanilla class shapes (`SpriteContents`, `NativeImage`, `ChunkBuilder`, etc.) changed and JNI proxies still target the old layout. Java port unblocks Java-only review; MCVR work is a separate dependency.

## Stage 0 — Build script + AccessWidener (do first, expect compile breaks)

- [ ] `gradle/wrapper/gradle-wrapper.properties`: bump to `gradle-9.4.0-bin.zip`.
- [ ] `gradle.properties`:
  - `minecraft_version=26.1.2`
  - drop `yarn_mappings`
  - `loader_version=0.18.4`
  - `loom_version=1.15-SNAPSHOT`
  - `fabric_version=0.148.0+26.1.2`
  - bump `mod_version` to e.g. `0.2.0-alpha`
- [ ] `build.gradle`:
  - plugin id `fabric-loom` → `net.fabricmc.fabric-loom`
  - drop the `mappings "..."` line
  - `modImplementation` → `implementation` for `fabric-loader`, `fabric-api`
  - rename task `remapJar` → `jar`, `remapSourcesJar` → `sourcesJar`
  - `targetJavaVersion = 21` → `25`
- [ ] `src/main/resources/radiance.accesswidener`: header `accessWidener v2 named` → `accessWidener v2 official`.
- [ ] `src/main/resources/fabric.mod.json`: `depends.minecraft` to `">=26.1 <26.2"`.
- [ ] First build attempt: `./gradlew build`. Expect a wall of "cannot find symbol" / mixin target errors — capture the output, decide rename strategy from there.

## Stage 1 — Java source rename (yarn → Mojang official) — **PARTIAL**

Automated rename complete using a yarn 1.21.4 → mojmap 1.21.4 bridge plus a
26.1 import-path correction pass and a small hand-curated 1.21.4→26.1 rename
table. Tooling under `.port-tools/` (gitignored). Build still failing — see
"Stage 1 residuals" below.



- [ ] `src/main/java/com/radiance/client/RadianceClient.java` — `MinecraftClient` → `Minecraft`, `runDirectory` accessor, etc.
- [ ] `src/main/java/com/radiance/client/proxy/vulkan/*.java` — every vanilla type referenced (NativeImage, Window, VertexFormat, ...).
- [ ] `src/main/java/com/radiance/client/proxy/world/*.java`.
- [ ] `src/main/java/com/radiance/client/option/Options.java` and `client/option/*` enums.
- [ ] `src/main/java/com/radiance/client/pipeline/**`.
- [ ] `src/main/java/com/radiance/client/gui/**` — Screen, OptionListWidget, EntryListWidget, Text, etc.
- [ ] `src/main/java/com/radiance/client/texture/**`.
- [ ] `src/main/java/com/radiance/client/util/**`.
- [ ] `src/main/java/com/radiance/client/vertex/**`.
- [ ] `src/main/java/com/radiance/client/shader/**`.

## Stage 1 residuals — **MC 26.1 API refactor (manual work required)**

The remaining ~200 compile errors are not yarn->mojmap mismatches. They are
real Mojang API removals/renames between 1.21.4 and 26.1. Mojang reworked the
rendering pipeline substantially. Top blockers, with what we know about the
26.1 replacement:

| Removed / renamed (1.21.4 mojmap)      | 26.1 status                                                     |
|----------------------------------------|-----------------------------------------------------------------|
| `net.minecraft.client.gui.GuiGraphics` | Removed. Only `GuiGraphicsExtractor` remains; rendering API rewritten. |
| `com.mojang.blaze3d.font.SheetGlyphInfo` | Renamed to `com.mojang.blaze3d.font.GlyphInfo`.               |
| `net.minecraft.client.renderer.CompiledShaderProgram` | Removed; shader pipeline reworked. |
| `com.mojang.blaze3d.platform.NativeImage.InternalFormat` | Inner enum removed/renamed.       |
| `net.minecraft.client.renderer.RenderStateShard` | Likely moved/refactored under new render pipeline. |
| `net.minecraft.client.renderer.chunk.RenderChunkRegion` | Renamed `RenderSectionRegion`. |
| `net.minecraft.client.color.block.BlockColor` | Removed/renamed; `BlockColors` registry remains. |
| `net.minecraft.util.OptionEnum` | Removed.                                                          |
| `net.minecraft.client.OptionInstance.TooltipFactory` | Inner type removed/renamed.                  |
| `CycleButton.Values` | Inner type removed/renamed.                                                  |
| `RenderType` location | Moved to `net.minecraft.client.renderer.rendertype.RenderType`.            |
| `BlockRenderDispatcher`, `LiquidBlockRenderer`, `ItemRenderer` | Subsystems likely restructured.   |
| `BufferUploader`, `VertexBuffer` (blaze3d.vertex) | Likely removed or rewritten under the new rendering. |
| `LightTexture`, `FogParameters`, `DimensionSpecialEffects`, `ShaderProgramConfig` | Likely removed/refactored. |

These cannot be fixed by mass rename. Each requires reading the 26.1 source and
deciding the new API call shape, often with non-trivial behavioral changes.

Suggested attack order (smallest blast radius first):

- [ ] `client/constant/VulkanConstants.java` — fix `NativeImage.InternalFormat` references.
- [ ] `client/constant/Constants.java` — `RenderType` import path; verify `VertexFormat.Mode` enum coverage.
- [ ] `client/gui/**` — replace `GuiGraphics` with whatever 26.1 ships (likely a new render-context class). Possibly the largest manual surface.
- [ ] `client/option/**` and `gui/PotentialValuesBasedCallbacksNoValue.java` — `OptionEnum` removal and `OptionInstance.CyclingCallbacks` rework.
- [ ] `mixins/vulkan_render_integration/**` — many target rewritten APIs (CompiledShaderProgram, RenderStateShard, BlockRenderDispatcher, ChunkBuilder/SectionRenderDispatcher rename, RenderChunkRegion, BufferUploader). Expect to rewrite injection points.
- [ ] `mixins/vanilla_resource_tracker/**` — font/glyph mixins target SheetGlyphInfo→GlyphInfo and possibly other renames.
- [ ] `client/proxy/vulkan/ShaderProxy.java`, `BufferProxy.java`, `TextureProxy.java` — verify against new shader/buffer/texture vanilla APIs.

Without a 26.1 fork of MCVR none of this is runtime-testable; the goal at this
stage is just a green `./gradlew compileJava`.

## Stage 2 — Mixins (`com.radiance.mixins.*`)

`defaultRequire = 1` and `requireAnnotations = true` make every miss fatal. Each file needs the `@Mixin` target updated and every `method = "..."` / `target = "..."` string rewritten to the official name.

- [ ] `vanilla_resource_tracker/*Mixins.java` (16 files).
- [ ] `vulkan_options/*Mixins.java` (2 files).
- [ ] `vulkan_render_integration/*Mixins.java` (~50 files — biggest chunk).
- [ ] `mixin_related/extensions/**/I*Ext.java` — interface signatures must align with new vanilla method names where they shadow them.
- [ ] `radiance.mixins.json` — package paths unchanged, but verify class list still matches files.

## Stage 3 — Resources / metadata

- [ ] `src/main/resources/assets/radiance/lang/*.json` — no rename, but check any keys that reference vanilla widgets (none currently expected).
- [ ] `src/main/resources/modules/*.yaml` — pure data, no port work.
- [ ] `radiance.accesswidener` body — every line: classes/methods/fields referenced are Mojang official names now (descriptors stay in JVM form).

## Stage 4 — JNI surface review (no change yet, document only)

- [ ] List every `public static native` method in `client/proxy/vulkan/*Proxy.java` and every `radiance$*` method in `mixin_related/extensions/**` against the vanilla class shapes used.
- [ ] Note which extensions reach into vanilla internals that *changed* in 26.1 (esp. `NativeImage`, `SpriteContents`, `ChunkBuilder.BuiltChunk`, `RenderPhase`, `CompiledShader`). These dictate MCVR-side rewrites.
- [ ] Output: a `MCVR-26.1-API.md` for the C++ team — out of scope of this branch, but produced from the rename diff.

## Stage 5 — Manual smoke (blocked on MCVR)

- [ ] `./gradlew runClient` — expect immediate crash on `RadianceClient.onInitializeClient` due to MCVR ABI mismatch.
- [ ] Until MCVR ships a 26.1 build, the port cannot be runtime-verified. Stop at green `./gradlew build`.

## Out of scope on this branch

- Any C++ / MCVR change.
- New rendering features.
- Refactors not required by the port.

## References

- Fabric porting guide: https://docs.fabricmc.net/develop/porting/
- Fabric for MC 26.1 announcement: https://fabricmc.net/2026/03/14/261.html
- Mappings migration: https://docs.fabricmc.net/develop/porting/mappings/
- MC 26.1.2 release notes: https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1-2
