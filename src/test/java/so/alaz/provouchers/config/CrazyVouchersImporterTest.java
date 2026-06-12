package so.alaz.provouchers.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CrazyVouchersImporterTest {

    @TempDir
    Path plugins;

    private CrazyVouchersImporter importer(VoucherRegistry registry) throws Exception {
        Path dataFolder = plugins.resolve("ProVouchers");
        Files.createDirectories(dataFolder);
        return new CrazyVouchersImporter(dataFolder.toFile(), registry);
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
              glowing: true
              has-argument: true
              commands:
                - 'lp user {player} parent set {arg}'
              items:
                - 'Item:diamond_sword, Name:&cA fancy sword, Lore:&eline one,&7line two, Amount:2, Glowing:true'
              options:
                message: '&aCongrats {player}!'
                sound:
                  toggle: true
                  volume: 1.0
                  pitch: 1.0
                  sounds:
                    - 'BLOCK.AMETHYST_BLOCK.STEP'
            """);
        CrazyVouchersImporter.Result result = importer(new VoucherRegistry()).importAll();

        assertThat(result.sourceFound()).isTrue();
        assertThat(result.imported()).containsExactly("rank-up");
        File out = plugins.resolve("ProVouchers/vouchers/rank-up.yml").toFile();
        assertThat(out).exists();

        // The written file must parse as a valid ProVouchers voucher with converted text.
        Voucher voucher = VoucherParser.parseVoucher(YamlConfiguration.loadConfiguration(out), "rank-up");
        assertThat(voucher.displayName()).isEqualTo("<aqua>Rank Up <yellow>%arg%");
        assertThat(voucher.hasArgument()).isTrue();
        assertThat(voucher.item().glow()).isTrue();
        assertThat(voucher.rewards()).hasSize(3);
        assertThat(voucher.rewards().get(0).payload()).isEqualTo("lp user %player% parent set %arg%");
        assertThat(voucher.definedItems()).containsKey("imported_1");
        assertThat(voucher.definedItems().get("imported_1").displayName()).isEqualTo("<red>A fancy sword");
        assertThat(voucher.definedItems().get("imported_1").lore())
            .containsExactly("<yellow>line one", "<gray>line two");
        assertThat(voucher.effects().sound()).startsWith("block.amethyst_block.step");
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

        CrazyVouchersImporter.Result result = importer(registry).importAll();
        assertThat(result.imported()).containsExactly("starter_kit");
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0)).contains("taken");
    }

    @Test
    void missingSourceFolderIsReported() throws Exception {
        CrazyVouchersImporter.Result result = importer(new VoucherRegistry()).importAll();
        assertThat(result.sourceFound()).isFalse();
    }

    private static YamlConfiguration yaml(String content) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(content);
        return config;
    }
}
