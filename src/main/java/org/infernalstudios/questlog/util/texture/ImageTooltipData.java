package org.infernalstudios.questlog.util.texture;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Validate image link data before it reaches the renderer. */
public record ImageTooltipData(ResourceLocation texture, int width, int height, int frames, int frameTime) {
    @Nullable
    public static ImageTooltipData parse(String value) {
        if (value == null) return null;
        String[] parts = value.split(":", -1);
        if (parts.length < 3 || parts.length > 7 || parts.length == 6 || !parts[0].equals("image")) return null;
        ResourceLocation texture = ResourceLocation.tryParse(parts[1] + ":" + parts[2]);
        if (texture == null) return null;
        try {
            int width = parts.length >= 4 ? Integer.parseInt(parts[3]) : 16;
            int height = parts.length >= 5 ? Integer.parseInt(parts[4]) : 16;
            int frames = parts.length == 7 ? Integer.parseInt(parts[5]) : 0;
            int frameTime = parts.length == 7 ? Integer.parseInt(parts[6]) : 0;
            if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE - 4 || height > Integer.MAX_VALUE - 4
                    || parts.length == 7 && (frames <= 0 || frameTime <= 0)) return null;
            if (frames > 0) Math.multiplyExact(height, frames);
            return new ImageTooltipData(texture, width, height, frames, frameTime);
        } catch (IllegalArgumentException | ArithmeticException ignored) {
            return null;
        }
    }
}
