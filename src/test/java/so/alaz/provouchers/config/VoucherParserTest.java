package so.alaz.provouchers.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import so.alaz.provouchers.reward.RewardType;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;

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
