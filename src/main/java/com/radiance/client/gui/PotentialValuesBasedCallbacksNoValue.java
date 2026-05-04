package com.radiance.client.gui;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.OptionInstance.TooltipFactory;

@Environment(EnvType.CLIENT)
public record PotentialValuesBasedCallbacksNoValue<T>(List<T> values, Codec<T> codec) implements
    OptionInstance.CyclingCallbacks<T> {

    @Override
    public Optional<T> validate(T value) {
        return this.values.contains(value) ? Optional.of(value) : Optional.empty();
    }

    @Override
    public CycleButton.Values<T> getValues() {
        return CycleButton.Values.of(this.values);
    }

    @Override
    public Function<OptionInstance<T>, AbstractWidget> getWidgetCreator(
        TooltipFactory<T> tooltipFactory, Options gameOptions, int x, int y, int width,
        Consumer<T> changeCallback) {
        return option -> CycleButton.<T>builder(option.textGetter)
            .values(this.getValues())
            .tooltip(tooltipFactory)
            .initially(option.getValue())
            .omitKeyText()
            .build(x, y, width, 20, option.text, (button, value) -> {
                this.valueSetter().set(option, value);
                gameOptions.write();
                changeCallback.accept(value);
            });
    }
}
