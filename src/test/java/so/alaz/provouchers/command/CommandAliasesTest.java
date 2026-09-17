package so.alaz.provouchers.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandAliasesTest {

    @Test
    void keepsValidAliasesInOrder() {
        assertThat(CommandAliases.normalize(List.of("pv", "vouchers"), "voucher"))
            .containsExactly("pv", "vouchers");
    }

    @Test
    void trimsLowerCasesAndDeduplicates() {
        assertThat(CommandAliases.normalize(List.of(" PV ", "pv", "Vouchers"), "voucher"))
            .containsExactly("pv", "vouchers");
    }

    @Test
    void dropsBlanksWhitespaceAndOwnLabel() {
        assertThat(CommandAliases.normalize(List.of("", "  ", "player vault", "Voucher", "rewards"), "voucher"))
            .containsExactly("rewards");
    }

    @Test
    void emptyListRegistersNoAliases() {
        assertThat(CommandAliases.normalize(List.of(), "stash")).isEmpty();
    }
}
