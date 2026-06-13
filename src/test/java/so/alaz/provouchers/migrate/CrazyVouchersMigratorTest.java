package so.alaz.provouchers.migrate;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import so.alaz.provouchers.config.VoucherParser;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CrazyVouchersMigratorTest {

    @TempDir
    Path plugins;

    private CrazyVouchersMigrator importer(VoucherRegistry registry) throws Exception {
        Path dataFolder = plugins.resolve("ProVouchers");
        Files.createDirectories(dataFolder);
        return new CrazyVouchersMigrator(dataFolder.toFile(), registry);
    }

    private Voucher importedVoucher(String id) {
        return VoucherParser.parseVoucher(YamlConfiguration.loadConfiguration(
            plugins.resolve("ProVouchers/vouchers/" + id + ".yml").toFile()), id);
    }

    @Test
    void importsAModernPerFileVoucher() throws Exception {
        Path cv = plugins.resolve("CrazyVouchers/vouchers");
        Files.createDirectories(cv);
        Files.writeString(cv.resolve("Rank-Up.yml"), """
            voucher:
              item: 'paper'
              name: '&bRank Up &e{arg}'
              lore:
                - '&7Right click to redeem.'
              glowing: 'add_glow'
              custom-model-data: -1
              has-argument: true
              commands:
                - 'lp user {player} parent set {arg}'
              items:
                - 'Item:diamond_sword, Name:&cA fancy sword, Lore:&eline one,&7line two, Amount:2, Glowing:true'
              random-commands:
                "1":
                  weight: 70.0
                  commands:
                    - '70 eco give {player} 100'
                "2":
                  weight: 30.0
                  commands:
                    - 'eco give {player} 1000'
              options:
                message: '&aCongrats {player}!'
                two-step-authentication: true
                sound:
                  toggle: true
                  volume: 1.0
                  pitch: 1.0
                  sounds:
                    - 'BLOCK.AMETHYST_BLOCK.STEP'
            """);
        CrazyVouchersMigrator migrator = importer(new VoucherRegistry());
        assertThat(migrator.isPresent()).isTrue();
        MigrationReport result = migrator.migrate();
        assertThat(result.imported()).containsExactly("voucher rank-up");

        Voucher voucher = importedVoucher("rank-up");
        assertThat(voucher.displayName()).isEqualTo("<aqua>Rank Up <yellow>%arg%");
        assertThat(voucher.hasArgument()).isTrue();
        assertThat(voucher.twoStep()).isTrue();
        assertThat(voucher.item().glow()).as("glowing: add_glow").isTrue();
        assertThat(voucher.item().customModelData()).as("-1 sentinel skipped").isNull();
        assertThat(voucher.rewards().get(0).payload()).isEqualTo("lp user %player% parent set %arg%");
        assertThat(voucher.definedItems().get("imported_1").displayName()).isEqualTo("<red>A fancy sword");
        assertThat(voucher.definedItems().get("imported_1").lore())
            .containsExactly("<yellow>line one", "<gray>line two");
        // The weighted random-commands map becomes two reward sets; the legacy inline 70 is stripped.
        assertThat(voucher.randomRewards()).hasSize(2);
        assertThat(voucher.randomRewards().get(0).rewards().get(0).payload())
            .isEqualTo("eco give %player% 100");
        assertThat(voucher.effects().sound()).startsWith("block.amethyst_block.step");
    }

    @Test
    void mapsGatesAndReportsEveryUnmappedKey() throws Exception {
        Path cv = plugins.resolve("CrazyVouchers/vouchers");
        Files.createDirectories(cv);
        Files.writeString(cv.resolve("Gated.yml"), """
            voucher:
              item: 'paper'
              override-anti-dupe: false
              allow-vouchers-in-item-frames: false
              display-damage: 50
              is-edible: false
              options:
                whitelist-worlds:
                  toggle: true
                  message: '{prefix}Wrong world.'
                  worlds: [ 'world', 'world_nether' ]
                limiter:
                  toggle: true
                  amount: 10
                permission:
                  whitelist-permission:
                    toggle: true
                    message: '{prefix}No permission.'
                    permissions: [ 'vouchers.use.gated' ]
                  blacklist-permission:
                    toggle: true
                    permissions: [ 'some.node' ]
            """);
        MigrationReport result = importer(new VoucherRegistry()).migrate();
        assertThat(result.imported()).containsExactly("voucher gated");

        Voucher voucher = importedVoucher("gated");
        assertThat(voucher.maxUses()).isEqualTo(10);
        assertThat(voucher.conditionMaps()).hasSize(2);
        assertThat(voucher.conditionMaps().get(0)).containsEntry("type", "world")
            .containsEntry("deny", "Wrong world.");
        assertThat(voucher.conditionMaps().get(1)).containsEntry("type", "permission")
            .containsEntry("permission", "vouchers.use.gated");
        // Every unmapped key is reported, not silently dropped.
        assertThat(result.warnings()).anyMatch(w -> w.contains("override-anti-dupe"))
            .anyMatch(w -> w.contains("allow-vouchers-in-item-frames"))
            .anyMatch(w -> w.contains("display-damage"))
            .anyMatch(w -> w.contains("is-edible"))
            .anyMatch(w -> w.contains("blacklist-permission"));
    }

    @Test
    void mapsItemDslDamageTrimAndEnchantments() throws Exception {
        Path cv = plugins.resolve("CrazyVouchers/vouchers");
        Files.createDirectories(cv);
        Files.writeString(cv.resolve("Trim.yml"), """
            voucher:
              item: 'paper'
              items:
                - 'Item:diamond_helmet, Damage:50, Trim:sentry!quartz, Amount:1, protection:4, unbreaking:3'
            """);
        MigrationReport result = importer(new VoucherRegistry()).migrate();

        var defined = importedVoucher("trim").definedItems().get("imported_1").item();
        assertThat(defined.material()).isEqualTo("DIAMOND_HELMET");
        assertThat(defined.damage()).isEqualTo(50);
        assertThat(defined.trim()).isEqualTo(new so.alaz.provouchers.voucher.ItemTrim("quartz", "sentry"));
        assertThat(defined.enchantments()).containsEntry("protection", 4).containsEntry("unbreaking", 3);
        // Everything mapped: no item-option warnings remain.
        assertThat(result.warnings()).noneMatch(w -> w.contains("item option"));
    }

    @Test
    void mapsCooldownPlaceholdersAndItemModel() throws Exception {
        Path cv = plugins.resolve("CrazyVouchers/vouchers");
        Files.createDirectories(cv);
        Files.writeString(cv.resolve("Full.yml"), """
            voucher:
              item: 'paper'
              cooldown:
                toggle: true
                interval: 3600
              components:
                item-model:
                  namespace: 'minecraft'
                  key: 'emerald'
                hide-tooltip: true
              options:
                required-placeholders-message: '{prefix}Not eligible.'
                required-placeholders:
                  one:
                    placeholder: '%player_level%'
                    value: '30'
            """);
        importer(new VoucherRegistry()).migrate();

        Voucher voucher = importedVoucher("full");
        assertThat(voucher.cooldownSeconds()).isEqualTo(3600);
        assertThat(voucher.item().itemModel()).isEqualTo("minecraft:emerald");
        assertThat(voucher.item().hideTooltip()).isTrue();
        assertThat(voucher.conditionMaps()).anyMatch(c -> "papi".equals(c.get("type"))
            && "%player_level%".equals(c.get("placeholder")) && "30".equals(c.get("value")));
    }

    @Test
    void importsCodes() throws Exception {
        Path cv = plugins.resolve("CrazyVouchers/codes");
        Files.createDirectories(cv);
        Files.writeString(cv.resolve("Starter-Money.yml"), """
            voucher-code:
              code: 'startermoney'
              commands:
                - 'eco give {player} 10000'
              options:
                case-sensitive: false
                message: '{prefix}<gray>You got $10,000.'
                limiter:
                  toggle: true
                  amount: 5
                sound:
                  toggle: true
                  sounds: [ 'block.note_block.pling' ]
            """);
        MigrationReport result = importer(new VoucherRegistry()).migrate();
        // The file id comes from the file name; the code value stays as configured.
        assertThat(result.imported()).containsExactly("code starter-money");

        VoucherCode code = VoucherParser.parseCode(YamlConfiguration.loadConfiguration(
            plugins.resolve("ProVouchers/codes/starter-money.yml").toFile()), "starter-money");
        assertThat(code.code()).isEqualTo("startermoney");
        assertThat(code.maxUses()).isEqualTo(5);
        assertThat(code.rewards().get(0).payload()).isEqualTo("eco give %player% 10000");
        // A code has no item, so sound is reported as voucher-only.
        assertThat(result.warnings()).anyMatch(w -> w.contains("sound/firework"));
    }

    @Test
    void importsLegacySectionsAndSkipsExistingIds() throws Exception {
        Path cv = plugins.resolve("CrazyVouchers");
        Files.createDirectories(cv);
        Files.writeString(cv.resolve("vouchers.yml"), """
            vouchers:
              Starter Kit:
                item: 'chest'
                name: '&6Starter'
                commands:
                  - 'kit starter {player}'
              taken:
                item: 'paper'
            """);
        VoucherRegistry registry = new VoucherRegistry();
        registry.register(VoucherParser.parseVoucher(yaml("item:\n  material: PAPER\n"), "taken"));

        MigrationReport result = importer(registry).migrate();
        assertThat(result.imported()).containsExactly("voucher starter_kit");
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0)).contains("taken");
    }

    @Test
    void missingSourceFolderIsNotPresent() throws Exception {
        assertThat(importer(new VoucherRegistry()).isPresent()).isFalse();
    }

    private static YamlConfiguration yaml(String content) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(content);
        return config;
    }
}
