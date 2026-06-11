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
