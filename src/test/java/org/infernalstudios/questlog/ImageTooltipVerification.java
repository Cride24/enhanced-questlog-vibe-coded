package org.infernalstudios.questlog;

import org.infernalstudios.questlog.util.texture.ImageTooltipData;

/** Covers the malformed link that crashed the client without opening a screen. */
public final class ImageTooltipVerification {
    public static void main(String[] args) {
        String[] invalid = {null, "image", "image:minecraft", "image:BAD:textures/test.png",
                "image:minecraft:textures/gui/icons.png:large:16", "image:minecraft:test.png:16:wide",
                "image:minecraft:test.png:0:16", "image:minecraft:test.png:16:-1",
                "image:minecraft:test.png:16:16:0:1", "image:minecraft:test.png:16:16:2:0",
                "image:minecraft:test.png:16:16:two:1", "image:minecraft:test.png:16:16:2",
                "image:minecraft:test.png:16:16:2:1:extra", "image:minecraft:test.png:16:",
                "image:minecraft:test.png:16:2000000000:2:1", "item:minecraft:stick"};
        for (String value : invalid) {
            if (ImageTooltipData.parse(value) != null) throw new AssertionError("Accepted invalid image: " + value);
        }
        ImageTooltipData plain = ImageTooltipData.parse("image:minecraft:textures/item/emerald.png");
        if (plain == null || plain.width() != 16 || plain.height() != 16 || plain.frames() != 0) throw new AssertionError("Default image");
        ImageTooltipData sized = ImageTooltipData.parse("image:minecraft:test.png:32:24");
        if (sized == null || sized.width() != 32 || sized.height() != 24) throw new AssertionError("Sized image");
        ImageTooltipData animated = ImageTooltipData.parse("image:minecraft:test.png:16:32:4:2");
        if (animated == null || animated.frames() != 4 || animated.frameTime() != 2) throw new AssertionError("Animated image");
        System.out.println("Image tooltips: 19 checks passed (headless, no renderer opened).");
    }
}
