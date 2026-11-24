package ink.myumoon.portholequest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Local UI feedback for explicit actions; document and snapshot lifecycles stay silent. */
public final class JournalSounds {
    private static final long PAPER_INTERVAL_NANOS = 150_000_000L;
    private static final long ERROR_INTERVAL_NANOS = 250_000_000L;
    // Match vanilla PageButton: forUI's default volume, before the resource's 2x multiplier.
    private static final float PAPER_VOLUME = 0.25F;
    private static long nextPaperAt;
    private static long nextErrorAt;

    private JournalSounds() {}

    public static void paper() {
        long now = System.nanoTime();
        if (now < nextPaperAt) return;
        nextPaperAt = now + PAPER_INTERVAL_NANOS;
        play(SoundEvents.BOOK_PAGE_TURN, 1.0F, PAPER_VOLUME);
    }

    public static void tab() {
        play(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F, 0.20F);
    }

    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.15F);
    }

    public static void error() {
        long now = System.nanoTime();
        if (now < nextErrorAt) return;
        nextErrorAt = now + ERROR_INTERVAL_NANOS;
        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.75F, 0.12F);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
