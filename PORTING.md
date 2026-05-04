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

## Stage 1 — Java source rename (yarn → Mojang official)

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
