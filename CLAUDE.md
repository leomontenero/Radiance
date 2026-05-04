# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Radiance is the Java/Fabric half of a Minecraft mod that replaces the vanilla OpenGL renderer with a Vulkan C++ backend (DLSS / FSR3 / XeSS / NRD / hardware ray tracing). The C++ renderer lives in a separate repo, **MCVR** (`https://github.com/Minecraft-Radiance/MCVR`), and is loaded as a native library (`core.dll` / `libcore.so`) at client init.

- Fabric mod, Minecraft `1.21.4`, Yarn mappings, Java **21**, fabric-loom `1.11-SNAPSHOT`.
- `mod_version` and other coordinates live in `gradle.properties`.

## Common commands

```bash
# Generate JNI headers (writes to src/main/native/include/) — do this before building MCVR
./gradlew compileJava

# Full build (requires MCVR built and installed first; see README "Build" section)
./gradlew build

# Run the client at 1920x1080 (configured in build.gradle)
./gradlew runClient

# Produce the remapped jar only
./gradlew remapJar
```

There is no test source set; do not invent test commands.

The `compileJava` task is configured `outputs.upToDateWhen { false }` so it always re-runs (JNI headers must stay in sync with native code). Do not "fix" this.

## Architecture

### Java ↔ native split

- `src/main/java/com/radiance/Radiance.java` — common entrypoint (no-op).
- `src/main/java/com/radiance/client/RadianceClient.java` — client entrypoint. On init it:
  1. Creates `<minecraft>/radiance/` runtime dir.
  2. Extracts native libs (`core.dll`/`core.lib` on Windows, `libcore.so` on Linux; optional XeSS DLLs) from the jar resources to that dir and `System.load`s them.
  3. Extracts `shaders/` and `modules/` resource folders to disk.
  4. Hands the runtime dir path to `RendererProxy.initFolderPath` and `Pipeline.initFolderPath`, then loads options and module entries.
- `client/proxy/vulkan/*Proxy.java` — thin Java façades over JNI; every renderer/buffer/texture/shader/window/draw-command/pipeline-state operation is a `native` method or wraps one. This is the only legitimate gateway to MCVR.
- `client/proxy/world/*Proxy.java` — JNI-side mirrors of chunk/entity/player state.
- DLSS DLLs (`nvngx_dlss.dll`, etc.) are deliberately **not** redistributed — users place them in `.minecraft/radiance/` themselves (NVIDIA license). Don't add code that downloads them.

### Mixin layout (Sponge mixins via Fabric)

- `radiance.mixins.json` — single mixin config; `package` is `com.radiance.mixins`; uses a custom `plugin: com.radiance.mixin_related.MixinPlugin` whose `ENABLED` flag is a global kill switch (`shouldApplyMixin` returns it).
- `injectors.defaultRequire = 1` and `overwrites.requireAnnotations = true` — failing/silent injects will hard-fail the build. When adding a mixin, expect to set explicit `@Inject(... require=N)` / `@Overwrite` annotations.
- Mixin sub-packages each have a focused job:
  - `vanilla_resource_tracker` — hook vanilla texture/font/glyph/atlas pipeline so the Vulkan renderer can mirror GPU resource state.
  - `vulkan_render_integration` — wholesale redirect of Mojang's `RenderSystem`, `WorldRenderer`, `ChunkBuilder`, `GameRenderer`, etc. into `RendererProxy`/`TextureProxy`/etc.
  - `vulkan_options` — splice the mod's video options into vanilla `GameOptionsScreen` / `VideoOptionsScreen`.
- `com.radiance.mixin_related.extensions.*` — `I*Ext` interfaces. These are *not* in the mixin config; they are interface mixins applied implicitly by `@Mixin(... interfaces=...)` or by mixin classes implementing them, and define `radiance$xxx` accessor methods added to vanilla classes (e.g. `INativeImageExt#radiance$loadFromTextureImageWithoutUI`). Always cast to the `I*Ext` interface to call these — never reflect.
- `radiance.accesswidener` (Loom-managed) — broadens visibility on Mojang internals (mostly font, sprite, RenderPhase, GlStateManager inner classes, SimpleOption fields). Add new entries here rather than reflecting.

### Render pipeline (`client/pipeline/`)

- `Pipeline` is a singleton describing the active graph of `Module`s. State is persisted to `<minecraft>/radiance/pipeline.yaml`.
- Module *definitions* (input/output image configs, attributes, defaults) are YAML files shipped in `src/main/resources/modules/` (`dlss.yaml`, `fsr_upscaler.yaml`, `nrd.yaml`, `xess_sr.yaml`, `ray_tracing.yaml`, `temporal_accumulation.yaml`, `tone_mapping.yaml`, `post_render.yaml`) and parsed via SnakeYAML (bundled via `include`).
- Module *names* and i18n keys are referenced as constants in `Pipeline.java` (`DLSS_MODULE_NAME`, etc.) — keep these in sync with the YAML `name:` field and lang files under `assets/radiance/lang/`.
- Shader packs for ray tracing live under `shaders/world/ray_tracing/` (`vanilla-pt.zip`, `restir-di.zip`) and external packs are loaded from vanilla `shaderpacks/` when manifested with a `radiance` block.
- `Pipeline` has two modes: `PRESET` (named preset from `Presets`) vs custom; check `mode` before mutating module list.

### Options & GUI

- `client/option/Options.java` — flat properties file (`<minecraft>/radiance/options.properties`) plus public static fields. All option keys / category keys are i18n keys defined as `public static final String` constants; mirror new options into lang files.
- `client/gui/` — custom screens: `RenderPipelineScreen`, `ModuleAttributeScreen`, `ShaderPackScreen`, `ShaderPackSettingsScreen`. Spliced into vanilla settings via the `vulkan_options` mixins.

## Conventions

- Prefer adding access via `radiance.accesswidener` or an `I*Ext` extension interface over reflection.
- New mixin classes must be registered in `radiance.mixins.json` — Loom will not auto-discover them.
- New native entry points: declare `public static native` in a `*Proxy` class, run `./gradlew compileJava` to regenerate the JNI header in `src/main/native/include/`, then implement on the MCVR side.
- The JDK redist note in the README (renaming `msvcp140.dll`/`vcruntime140*.dll` in the JDK `bin/`) is a *user-side workaround* for an MSVC bug — never code around it or auto-modify the user's JDK.
- Mod is `"environment": "client"`; do not add server-side logic to `RadianceClient` or new `client/` code.
