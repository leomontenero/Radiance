package com.radiance.mixins.vulkan_options;

import static net.minecraft.client.Options.getGenericValueText;
import static net.minecraft.client.InactivityFpsLimit.AFK;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.gui.PotentialValuesBasedCallbacksNoValue;
import com.radiance.client.gui.RenderPipelineScreen;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.OptionInstance;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public class VideoOptionsScreenMixins extends GameOptionsScreenMixins {

    @Unique
    private static final Component INACTIVITY_FPS_LIMIT_MINIMIZED_TOOLTIP = Component.translatable(
        "options.inactivityFpsLimit.minimized.tooltip");
    @Unique
    private static final Component INACTIVITY_FPS_LIMIT_AFK_TOOLTIP = Component.translatable(
        "options.inactivityFpsLimit.afk.tooltip");

    @Unique
    private static final PotentialValuesBasedCallbacksNoValue<Boolean> BOOLEAN_NO_KEY = new PotentialValuesBasedCallbacksNoValue<>(
        ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL
    );

    @Inject(method = "addOptions()V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectAddOptions(CallbackInfo ci) {
        OptionInstance<Integer>
            maxFps =
            new OptionInstance<>("options.framerateLimit",
                OptionInstance.emptyTooltip(),
                (optionText, value) -> value == 260 ?
                    getGenericValueText(optionText, Component.translatable("options.framerateLimit.max"))
                    :
                        getGenericValueText(optionText,
                            Component.translatable("options.framerate", value)),
                new OptionInstance.ValidatingIntSliderCallbacks(1, 26).withModifier(
                    value -> value * 10, value -> value / 10),
                Codec.intRange(10, 260),
                Options.maxFps,
                value -> {
                    Minecraft.getInstance()
                        .getInactivityFpsLimiter()
                        .setMaxFps(value);
                    Options.setMaxFps(value, true);
                });

        int i = -1;
        Window
            window =
            Minecraft.getInstance()
                .getWindow();
        Monitor monitor = window.getMonitor();
        int j;
        if (monitor == null) {
            j = -1;
        } else {
            Optional<VideoMode> optional = window.getFullscreenVideoMode();
            j =
                optional.map(monitor::findClosestVideoModeIndex)
                    .orElse(-1);
        }

        OptionInstance<Integer>
            fullScreenResolutionOption =
            new OptionInstance<>("options.fullscreen.resolution", OptionInstance.emptyTooltip(),
                (optionText, value) -> {
                    if (monitor == null) {
                        return Component.translatable("options.fullscreen.unavailable");
                    } else if (value == -1) {
                        return getGenericValueText(optionText,
                            Component.translatable("options.fullscreen.current"));
                    } else {
                        VideoMode videoMode = monitor.getVideoMode(value);
                        return getGenericValueText(optionText,
                            Component.translatable("options.fullscreen.entry",
                                videoMode.getWidth(),
                                videoMode.getHeight(),
                                videoMode.getRefreshRate(),
                                videoMode.getRedBits() + videoMode.getGreenBits() +
                                    videoMode.getBlueBits()));
                    }
                }, new OptionInstance.ValidatingIntSliderCallbacks(-1,
                monitor != null ? monitor.getVideoModeCount() - 1 : -1), j, value -> {
                if (monitor != null) {
                    window.setFullscreenVideoMode(
                        value == -1 ? Optional.empty() : Optional.of(monitor.getVideoMode(value)));
                }
            });

        OptionInstance<InactivityFpsLimit> inactivityFpsLimit = new OptionInstance<>(
            "options.inactivityFpsLimit",
            option -> {
                return switch (option) {
                    case MINIMIZED -> Tooltip.of(
                        INACTIVITY_FPS_LIMIT_MINIMIZED_TOOLTIP);
                    case AFK -> Tooltip.of(INACTIVITY_FPS_LIMIT_AFK_TOOLTIP);
                };
            },
            OptionInstance.enumValueText(),
            new OptionInstance.PotentialValuesBasedCallbacks<>(Arrays.asList(
                InactivityFpsLimit.values()),
                InactivityFpsLimit.Codec),
            AFK,
            inactivityLimit -> {
                Options.setInactivityFpsLimit(
                    inactivityLimit == AFK ? 30 : 9, true);
            });

        OptionInstance<Boolean> enableVsync = OptionInstance.ofBoolean("options.vsync", Options.vsync,
            value -> {
                if (Minecraft.getInstance()
                    .getWindow() != null) {
                    Options.setVsync(value, true);
                }
            });

        OptionInstance<Integer>
            chunkBuildingBatchSize =
            new OptionInstance<>(Options.CHUNK_BUILDING_BATCH_SIZE_KEY,
                OptionInstance.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Component.literal(Integer.toString(value))),
                new OptionInstance.ValidatingIntSliderCallbacks(1, 32),
                Codec.intRange(1, 32),
                Options.chunkBuildingBatchSize,
                value -> {
                    Options.setChunkBuildingBatchSize(value, true);
                });

        OptionInstance<Integer>
            chunkBuildingTotalBatches =
            new OptionInstance<>(Options.CHUNK_BUILDING_TOTAL_BATCHES_KEY,
                OptionInstance.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Component.literal(Integer.toString(value))),
                new OptionInstance.ValidatingIntSliderCallbacks(1, 32),
                Codec.intRange(1, 32),
                Options.chunkBuildingTotalBatches,
                value -> {
                    Options.setChunkBuildingTotalBatches(value, true);
                });

        OptionInstance<Integer>
            chunkBuildingThreads =
            new OptionInstance<>(Options.CHUNK_BUILDING_THREADS_KEY, OptionInstance.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Component.literal(Integer.toString(value))),
                new OptionInstance.ValidatingIntSliderCallbacks(1,
                    Options.getMaxChunkBuildingThreads()),
                Codec.intRange(1, Options.getMaxChunkBuildingThreads()),
                Options.chunkBuildingThreads,
                value -> Options.setChunkBuildingThreads(value, true));

        OptionInstance<Boolean> collectChunkEmission = OptionInstance.ofBoolean(
            Options.COLLECT_CHUNK_EMISSION_KEY,
            Options.collectChunkEmission,
            value -> Options.setCollectChunkEmission(value, true));

        OptionInstance<Boolean> pipelineSettings = new OptionInstance<>(Options.PIPELINE_SETUP_KEY,
            OptionInstance.emptyTooltip(),
            (optionText, value) -> optionText,
            BOOLEAN_NO_KEY,
            false,
            value -> {
                Minecraft.getInstance()
                    .setScreen(new RenderPipelineScreen((VideoSettingsScreen) (Object) this));
            });

        // Adding categories and options
        this.body.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_GAMEPLAY), body));
        OptionInstance[] optionsGameplay = new OptionInstance[]{ //
            gameOptions.getGraphicsMode(), //
            gameOptions.getViewDistance(), //
            gameOptions.getSimulationDistance(), //
            gameOptions.getGuiScale(), //
            gameOptions.getAttackIndicator(), //
            gameOptions.getGamma(), //
            gameOptions.getCloudRenderMode(), //
            gameOptions.getParticles(), //
            gameOptions.getDistortionEffectScale(), //
            gameOptions.getEntityDistanceScaling(), //
            gameOptions.getFovEffectScale(), //
            gameOptions.getShowAutosaveIndicator(), //
            gameOptions.getGlintSpeed(), //
            gameOptions.getGlintStrength(), //
            gameOptions.getMenuBackgroundBlurriness(), //
            gameOptions.getBobView(), //
        };
        this.body.addSingleOptionEntry(gameOptions.getBiomeBlendRadius());
        this.body.addSingleOptionEntry(gameOptions.getMipmapLevels());
        this.body.addAll(optionsGameplay);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_WINDOW), body));
        OptionInstance[] optionsWindow = new OptionInstance[]{ //
            maxFps, //
            inactivityFpsLimit, //
            enableVsync, //
            gameOptions.getFullscreen(), //
        };
        this.body.addAll(optionsWindow);
        this.body.addSingleOptionEntry(fullScreenResolutionOption);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_TERRAIN), body));
        this.body.addSingleOptionEntry(chunkBuildingBatchSize);
        this.body.addSingleOptionEntry(chunkBuildingTotalBatches);
        this.body.addSingleOptionEntry(chunkBuildingThreads);
        this.body.addSingleOptionEntry(collectChunkEmission);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_PIPELINE), body));
        this.body.addSingleOptionEntry(pipelineSettings);

        ci.cancel();
    }
}
