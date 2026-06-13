package so.alaz.provouchers.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import so.alaz.provouchers.reward.RewardType;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoucherParserTest {

    private static YamlConfiguration yaml(String content) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(content);
        return config;
    }

    @Test
    void parsesAFullVoucher() throws Exception {
        YamlConfiguration config = yaml("""
            display-name: "<gold>Crate Key"
            item:
              material: TRIPWIRE_HOOK
              custom-model-data: 42
              glow: true
            lore:
              - "<gray>line one"
            owner-only: true
            cooldown: 30
            has-argument: true
            conditions:
              - type: permission
                value: "provouchers.use.key"
            rewards:
              - "command: give %player% diamond 1"
              - "message: <green>done"
            random-rewards:
              - weight: 80
                rewards:
                  - "command: give %player% gold_ingot 1"
              - weight: 20
                rewards:
                  - "broadcast: <gold>rare!"
            """);

        Voucher voucher = VoucherParser.parseVoucher(config, "key");

        assertThat(voucher.id()).isEqualTo("key");
        assertThat(voucher.displayName()).isEqualTo("<gold>Crate Key");
        assertThat(voucher.item().material()).isEqualTo("TRIPWIRE_HOOK");
        assertThat(voucher.item().customModelData()).isEqualTo(42);
        assertThat(voucher.item().glow()).isTrue();
        assertThat(voucher.lore()).containsExactly("<gray>line one");
        assertThat(voucher.ownerOnly()).isTrue();
        assertThat(voucher.cooldownSeconds()).isEqualTo(30);
        assertThat(voucher.hasArgument()).isTrue();
        assertThat(voucher.conditionMaps()).hasSize(1);
        assertThat(voucher.conditionMaps().get(0)).containsEntry("type", "permission");
        assertThat(voucher.rewards()).hasSize(2);
        assertThat(voucher.rewards().get(0).type()).isEqualTo(RewardType.CONSOLE_COMMAND);
        assertThat(voucher.randomRewards()).hasSize(2);
        assertThat(voucher.randomRewards().get(0).weight()).isEqualTo(80.0);
    }

    @Test
    void defaultsApplyWhenKeysOmitted() throws Exception {
        Voucher voucher = VoucherParser.parseVoucher(yaml("item:\n  material: PAPER\n"), "plain");
        assertThat(voucher.displayName()).isNull();   // null lets a custom item keep its own name
        assertThat(voucher.cooldownSeconds()).isZero();
        assertThat(voucher.expiry()).isNull();
        assertThat(voucher.hasRewards()).isFalse();
        assertThat(voucher.stackable()).isTrue();      // stackable (anti-dupe off) by default
    }

    @Test
    void stackableCanBeDisabledForAntiDupe() throws Exception {
        Voucher antiDupe = VoucherParser.parseVoucher(
            yaml("item:\n  material: PAPER\nstackable: false\n"), "guarded");
        assertThat(antiDupe.stackable()).isFalse();
    }

    @Test
    void batchOpenParsesWhenStackableAndNoCooldown() throws Exception {
        Voucher voucher = VoucherParser.parseVoucher(
            yaml("item:\n  material: PAPER\nbatch-open: true\n"), "crate");
        assertThat(voucher.batchOpen()).isTrue();
    }

    @Test
    void batchOpenRejectedWithCooldown() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: PAPER\nbatch-open: true\ncooldown: 30\n");
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("cooldown");
    }

    @Test
    void batchOpenRejectedWhenNotStackable() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: PAPER\nbatch-open: true\nstackable: false\n");
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("stackable");
    }

    @Test
    void parsesDefinedItemsAndRefs() throws Exception {
        YamlConfiguration config = yaml("""
            item:
              material: PAPER
            items:
              vip_sword:
                material: DIAMOND_SWORD
                name: "<gold>Excalibur"
                lore:
                  - "<gray>Legendary"
                glow: true
            rewards:
              - "item: @vip_sword 2"
            """);
        Voucher voucher = VoucherParser.parseVoucher(config, "kit");
        assertThat(voucher.definedItems()).containsKey("vip_sword");
        assertThat(voucher.definedItems().get("vip_sword").displayName()).isEqualTo("<gold>Excalibur");
        assertThat(voucher.definedItems().get("vip_sword").item().glow()).isTrue();
    }

    @Test
    void undefinedItemRefIsRejected() throws Exception {
        YamlConfiguration config = yaml("""
            item:
              material: PAPER
            rewards:
              - "item: @missing 1"
            """);
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void parsesEnchantments() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: DIAMOND_SWORD\n  enchantments:\n"
            + "    sharpness: 5\n    Unbreaking: 3\n");
        Voucher voucher = VoucherParser.parseVoucher(config, "ench");
        assertThat(voucher.item().enchantments())
            .containsEntry("sharpness", 5).containsEntry("unbreaking", 3);
    }

    @Test
    void rejectsNonPositiveEnchantmentLevel() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: PAPER\n  enchantments:\n    sharpness: 0\n");
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class).hasMessageContaining("level");
    }

    @Test
    void parsesDamageUnbreakableAndTrim() throws Exception {
        YamlConfiguration config = yaml("""
            item:
              material: DIAMOND_CHESTPLATE
              damage: 50
              unbreakable: true
              trim:
                material: Quartz
                pattern: Sentry
            """);
        Voucher voucher = VoucherParser.parseVoucher(config, "armor");
        assertThat(voucher.item().damage()).isEqualTo(50);
        assertThat(voucher.item().unbreakable()).isTrue();
        assertThat(voucher.item().trim()).isEqualTo(new so.alaz.provouchers.voucher.ItemTrim("quartz", "sentry"));
    }

    @Test
    void incompleteTrimIsRejected() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: DIAMOND_HELMET\n  trim:\n    material: quartz\n");
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class).hasMessageContaining("trim");
    }

    @Test
    void parsesUseLimits() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: PAPER\nmax-uses: 100\nuses-per-player: 2\n");
        Voucher voucher = VoucherParser.parseVoucher(config, "limited");
        assertThat(voucher.maxUses()).isEqualTo(100);
        assertThat(voucher.usesPerPlayer()).isEqualTo(2);
        assertThat(voucher.hasUseLimits()).isTrue();
        Voucher unlimited = VoucherParser.parseVoucher(yaml("item:\n  material: PAPER\n"), "free");
        assertThat(unlimited.hasUseLimits()).isFalse();
    }

    @Test
    void longIdWithUseLimitsIsRejected() throws Exception {
        String longId = "a".repeat(57);
        YamlConfiguration config = yaml("item:\n  material: PAPER\nmax-uses: 5\n");
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, longId))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("56");
        // The same id without limits loads fine; only the use counter has the length bound.
        assertThat(VoucherParser.parseVoucher(yaml("item:\n  material: PAPER\n"), longId).id())
            .isEqualTo(longId);
    }

    @Test
    void zeroUseLimitIsRejected() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: PAPER\nmax-uses: 0\n");
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("max-uses");
    }

    @Test
    void parsesAbsoluteActiveFrom() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: PAPER\nactive-from: \"2026-07-01\"\n");
        assertThat(VoucherParser.parseVoucher(config, "event").activeFrom()).isEqualTo("2026-07-01");
    }

    @Test
    void relativeActiveFromIsRejected() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: PAPER\nactive-from: \"30d\"\n");
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("active-from");
    }

    @Test
    void parsesSoulboundShorthandAndToggles() throws Exception {
        Voucher all = VoucherParser.parseVoucher(yaml("item:\n  material: PAPER\nsoulbound: true\n"), "sb");
        assertThat(all.soulbound()).isEqualTo(new so.alaz.provouchers.voucher.SoulboundSpec(true, true, true));
        Voucher partial = VoucherParser.parseVoucher(yaml("""
            item:
              material: PAPER
            soulbound:
              block-drop: false
            """), "sb2");
        assertThat(partial.soulbound().blockDrop()).isFalse();
        assertThat(partial.soulbound().blockContainers()).isTrue();
        assertThat(VoucherParser.parseVoucher(yaml("item:\n  material: PAPER\nsoulbound: false\n"), "sb3")
            .soulbound()).isNull();
    }

    @Test
    void parsesEffectsBlock() throws Exception {
        YamlConfiguration config = yaml("""
            item:
              material: PAPER
            effects:
              sound: "minecraft:entity.player.levelup 1 1"
            """);
        Voucher voucher = VoucherParser.parseVoucher(config, "fx");
        assertThat(voucher.effects()).isNotNull();
        assertThat(voucher.effects().sound()).isEqualTo("minecraft:entity.player.levelup 1 1");
    }

    @Test
    void noEffectsBlockMeansNullEffects() throws Exception {
        Voucher voucher = VoucherParser.parseVoucher(yaml("item:\n  material: PAPER\n"), "plain");
        assertThat(voucher.effects()).isNull();
    }

    @Test
    void missingItemSectionIsRejected() throws Exception {
        assertThatThrownBy(() -> VoucherParser.parseVoucher(yaml("display-name: x\n"), "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("item");
    }

    @Test
    void conditionWithoutTypeIsRejected() throws Exception {
        YamlConfiguration config = yaml("""
            item:
              material: PAPER
            conditions:
              - value: "no type here"
            """);
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("type");
    }

    @Test
    void unknownRewardTypeIsRejected() throws Exception {
        YamlConfiguration config = yaml("""
            item:
              material: PAPER
            rewards:
              - "teleport: spawn"
            """);
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class);
    }

    @Test
    void invalidMaterialIsRejectedAtLoad() throws Exception {
        assertThatThrownBy(() -> VoucherParser.parseVoucher(yaml("item:\n  material: DIMAOND\n"), "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("material");
    }

    @Test
    void malformedCustomItemIsRejectedAtLoad() throws Exception {
        YamlConfiguration config = yaml("""
            item:
              material: PAPER
              custom: "itemsadder:"
            """);
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("custom");
    }

    @Test
    void unknownItemProviderIsRejectedAtLoad() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: PAPER\n  custom: \"oraxin:sword\"\n");
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("provider");
    }

    @Test
    void knownItemProvidersAreAccepted() throws Exception {
        for (String ref : List.of("itemsadder:ns:id", "oraxen:id", "nexo:id", "headdatabase:7129", "hdb:7129", "ia:foo")) {
            Voucher voucher = VoucherParser.parseVoucher(
                yaml("item:\n  material: PAPER\n  custom: \"" + ref + "\"\n"), "ok");
            assertThat(voucher.item().customItem()).isEqualTo(ref);
        }
    }

    @Test
    void malformedExpiryIsRejectedAtLoad() throws Exception {
        YamlConfiguration config = yaml("item:\n  material: PAPER\nexpiry: \"1 month\"\n");
        assertThatThrownBy(() -> VoucherParser.parseVoucher(config, "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("expiry");
    }

    @Test
    void validCustomItemAndRelativeExpiryAccepted() throws Exception {
        Voucher voucher = VoucherParser.parseVoucher(yaml("""
            item:
              material: PAPER
              custom: "itemsadder:ax_wings_pack:phoenix_wings"
            expiry: "30d"
            """), "ok");
        assertThat(voucher.item().customItem()).isEqualTo("itemsadder:ax_wings_pack:phoenix_wings");
        assertThat(voucher.expiry()).isEqualTo("30d");
    }

    @Test
    void parsesSkullItem() throws Exception {
        YamlConfiguration config = yaml("""
            item:
              skull:
                source: texture
                value: "eyJ0ZXh0dXJlcyI6..."
            """);
        var voucher = VoucherParser.parseVoucher(config, "head");
        assertThat(voucher.item().skull()).isNotNull();
        assertThat(voucher.item().skull().source())
            .isEqualTo(so.alaz.provouchers.voucher.SkullSpec.Source.TEXTURE);
    }

    @Test
    void rejectsSkullWithUnknownSourceOrBadUuid() throws Exception {
        assertThatThrownBy(() -> VoucherParser.parseVoucher(
            yaml("item:\n  skull:\n    source: hologram\n    value: x\n"), "bad"))
            .isInstanceOf(VoucherParseException.class)
            .hasMessageContaining("skull");
        assertThatThrownBy(() -> VoucherParser.parseVoucher(
            yaml("item:\n  skull:\n    source: uuid\n    value: not-a-uuid\n"), "bad"))
            .isInstanceOf(VoucherParseException.class);
    }

    @Test
    void parsesACode() throws Exception {
        YamlConfiguration config = yaml("""
            code: WELCOME
            case-sensitive: true
            max-uses: 50
            uses-per-player: 3
            rewards:
              - "message: <green>hi"
            """);
        VoucherCode code = VoucherParser.parseCode(config, "welcome");
        assertThat(code.code()).isEqualTo("WELCOME");
        assertThat(code.caseSensitive()).isTrue();
        assertThat(code.maxUses()).isEqualTo(50);
        assertThat(code.usesPerPlayer()).isEqualTo(3);
        assertThat(code.hasGlobalLimit()).isTrue();
    }

    @Test
    void zeroUsesPerPlayerRejected() throws Exception {
        YamlConfiguration config = yaml("code: X\nuses-per-player: 0\n");
        assertThatThrownBy(() -> VoucherParser.parseCode(config, "x"))
            .isInstanceOf(VoucherParseException.class);
    }
}
