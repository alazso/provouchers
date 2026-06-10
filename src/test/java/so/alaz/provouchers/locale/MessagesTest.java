package so.alaz.provouchers.locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MessagesTest {

    @TempDir
    Path dir;

    @Test
    void resolvesBundledEnglishAndFillsPlaceholders() {
        Messages messages = new Messages(dir.toFile(), "en", false);
        assertThat(messages.getForLocale(null, "redeem.cooldown", "seconds", 5))
            .isEqualTo("<red>You must wait 5s before redeeming this again.");
    }

    @Test
    void substitutesPrefixToken() {
        Messages messages = new Messages(dir.toFile(), "en", false);
        String out = messages.getForLocale(null, "command.give.success",
            "amount", 2, "voucher", "vip", "target", "Steve");
        assertThat(out).contains("ProVouchers").contains("Gave <yellow>2x").contains("vip").contains("Steve");
    }

    @Test
    void unknownKeyReturnsVisibleMarker() {
        Messages messages = new Messages(dir.toFile(), "en", false);
        assertThat(messages.getForLocale(null, "no.such.key")).isEqualTo("<red>missing:no.such.key");
    }

    @Test
    void perPlayerServesClientLanguageThenFallsBackToEnglishPerKey() throws Exception {
        Path lang = dir.resolve("lang");
        Files.createDirectories(lang);
        // A deliberately partial German translation: only one key is translated.
        Files.writeString(lang.resolve("de.yml"), "redeem:\n  expired: \"<red>Abgelaufen.\"\n");

        Messages messages = new Messages(dir.toFile(), "en", true);

        assertThat(messages.getForLocale(Locale.GERMAN, "redeem.expired")).isEqualTo("<red>Abgelaufen.");
        // A key the translation omits falls back to bundled English, never blank.
        assertThat(messages.getForLocale(Locale.GERMAN, "redeem.unredeemable"))
            .isEqualTo("<red>This voucher cannot be redeemed.");
    }

    @Test
    void perPlayerOffUsesDefaultRegardlessOfClientLanguage() throws Exception {
        Path lang = dir.resolve("lang");
        Files.createDirectories(lang);
        Files.writeString(lang.resolve("de.yml"), "redeem:\n  expired: \"<red>Abgelaufen.\"\n");

        Messages messages = new Messages(dir.toFile(), "en", false);

        assertThat(messages.getForLocale(Locale.GERMAN, "redeem.expired"))
            .isEqualTo("<red>This voucher has expired.");
    }
}
