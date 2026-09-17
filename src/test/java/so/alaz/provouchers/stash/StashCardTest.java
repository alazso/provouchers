package so.alaz.provouchers.stash;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StashCardTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private static StashEntry lasting(String voucherId, int amount, String arg, long createdAt) {
        return new StashEntry(UUID.randomUUID(), PLAYER, voucherId, amount, arg, StashSource.OVERFLOW,
            createdAt, null);
    }

    private static StashEntry expiring(String voucherId, int amount, String arg, long createdAt, long expiresAt) {
        return new StashEntry(UUID.randomUUID(), PLAYER, voucherId, amount, arg, StashSource.OFFLINE_GIVE,
            createdAt, expiresAt);
    }

    @Test
    void sumsNeverExpiringEntriesOfOneVoucherAndArgumentIntoOneCard() {
        StashEntry first = lasting("loot", 2, "vip", 100);
        StashEntry second = lasting("loot", 3, "vip", 200);
        assertThat(StashCard.group(List.of(first, second))).singleElement().satisfies(card -> {
            assertThat(card.amount()).isEqualTo(5);
            assertThat(card.entries()).containsExactly(first, second);
            assertThat(card.voucherId()).isEqualTo("loot");
            assertThat(card.arg()).isEqualTo("vip");
            assertThat(card.expiresAt()).isNull();
        });
    }

    @Test
    void nullArgumentGroupsOnlyWithNull() {
        StashEntry plain = lasting("loot", 1, null, 100);
        StashEntry vip = lasting("loot", 1, "vip", 200);
        StashEntry plainAgain = lasting("loot", 4, null, 300);
        assertThat(StashCard.group(List.of(plain, vip, plainAgain))).extracting(StashCard::entries)
            .containsExactly(List.of(plain, plainAgain), List.of(vip));
    }

    @Test
    void differentVouchersAndPlayersStaySeparate() {
        StashEntry loot = lasting("loot", 1, null, 100);
        StashEntry crate = lasting("crate", 1, null, 200);
        StashEntry otherPlayer = new StashEntry(UUID.randomUUID(), UUID.randomUUID(), "loot", 1, null,
            StashSource.API, 300, null);
        assertThat(StashCard.group(List.of(loot, crate, otherPlayer))).hasSize(3);
    }

    @Test
    void voucherIdsGroupIgnoringCase() {
        // The registry resolves ids ignoring case, so both rows are the same reward.
        StashEntry lower = lasting("loot", 1, null, 100);
        StashEntry upper = lasting("LOOT", 2, null, 200);
        assertThat(StashCard.group(List.of(upper, lower))).singleElement().satisfies(card -> {
            assertThat(card.amount()).isEqualTo(3);
            assertThat(card.voucherId()).isEqualTo("loot");   // as the oldest entry stores it
        });
    }

    @Test
    void expiringEntriesStayOneCardEach() {
        StashEntry soon = expiring("loot", 1, null, 100, 5_000);
        StashEntry later = expiring("loot", 1, null, 200, 9_000);
        StashEntry kept = lasting("loot", 2, null, 300);
        List<StashCard> cards = StashCard.group(List.of(kept, later, soon));
        assertThat(cards).extracting(StashCard::expiresAt).containsExactly(5_000L, 9_000L, null);
        assertThat(cards).extracting(StashCard::amount).containsExactly(1, 1, 2);
    }

    @Test
    void legacyMergedAndDuplicateRowsGroupWithoutMigration() {
        // Earlier versions merged deliveries into one row (amount 7), and their insert race left duplicates.
        StashEntry merged = lasting("loot", 7, null, 100);
        StashEntry duplicate = lasting("loot", 1, null, 100);
        StashEntry duplicateAgain = lasting("loot", 1, null, 100);
        assertThat(StashCard.group(List.of(duplicate, merged, duplicateAgain))).singleElement()
            .satisfies(card -> {
                assertThat(card.amount()).isEqualTo(9);
                assertThat(card.entries()).containsExactlyInAnyOrder(merged, duplicate, duplicateAgain);
            });
    }

    @Test
    void ordersCardsAndEntriesOldestFirstWhateverTheInputOrder() {
        StashEntry tieHigh = new StashEntry(new UUID(0, 2), PLAYER, "loot", 1, null, StashSource.API, 100, null);
        StashEntry tieLow = new StashEntry(new UUID(0, 1), PLAYER, "loot", 1, null, StashSource.API, 100, null);
        StashEntry crate = lasting("crate", 1, null, 50);
        StashEntry gift = expiring("gift", 1, null, 75, 10_000);
        List<StashCard> cards = StashCard.group(List.of(tieHigh, gift, tieLow, crate));
        assertThat(cards).extracting(StashCard::voucherId).containsExactly("crate", "gift", "loot");
        assertThat(cards.get(2).entries()).containsExactly(tieLow, tieHigh);   // same millisecond: the id decides
        assertThat(StashCard.group(List.of(crate, tieLow, gift, tieHigh))).isEqualTo(cards);
    }

    @Test
    void groupThatWouldOverflowAnIntContinuesOnAnotherCard() {
        StashEntry huge = lasting("loot", Integer.MAX_VALUE, null, 100);
        StashEntry one = lasting("loot", 1, null, 200);
        StashEntry two = lasting("loot", 2, null, 300);
        assertThat(StashCard.group(List.of(two, huge, one))).extracting(StashCard::amount)
            .containsExactly(Integer.MAX_VALUE, 3);
    }

    @Test
    void noEntriesMakeNoCards() {
        assertThat(StashCard.group(List.of())).isEmpty();
    }

    @Test
    void rejectsInvalidCards() {
        StashEntry loot = lasting("loot", 1, null, 100);
        assertThatThrownBy(() -> new StashCard(null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one");
        assertThatThrownBy(() -> new StashCard(List.of()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one");
        assertThatThrownBy(() -> new StashCard(List.of(loot, lasting("crate", 1, null, 200))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("share");
        assertThatThrownBy(() -> new StashCard(List.of(loot, lasting("loot", 1, "vip", 200))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("share");
        StashEntry otherPlayer = new StashEntry(UUID.randomUUID(), UUID.randomUUID(), "loot", 1, null,
            StashSource.API, 200, null);
        assertThatThrownBy(() -> new StashCard(List.of(loot, otherPlayer)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("share");
        assertThatThrownBy(() -> new StashCard(List.of(loot, expiring("loot", 1, null, 200, 9_000))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("card of its own");
        assertThatThrownBy(() -> new StashCard(List.of(lasting("loot", Integer.MAX_VALUE, null, 50), loot)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fit an int");
    }

    @Test
    void cardCopiesAndSortsItsEntries() {
        StashEntry older = lasting("loot", 1, null, 100);
        StashEntry newer = lasting("loot", 2, null, 200);
        List<StashEntry> source = new ArrayList<>(List.of(newer, older));
        StashCard card = new StashCard(source);
        source.clear();
        assertThat(card.entries()).containsExactly(older, newer);
        assertThatThrownBy(() -> card.entries().add(older)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void claimedSubsetIsACardOfJustThoseRows() {
        // A claim that lost a row to another session grants a card of only the rows it removed.
        StashEntry first = lasting("loot", 1, "vip", 100);
        StashEntry second = lasting("loot", 2, "vip", 200);
        StashEntry third = lasting("loot", 4, "vip", 300);
        assertThat(StashCard.group(List.of(first, second, third))).singleElement()
            .satisfies(card -> assertThat(card.amount()).isEqualTo(7));
        StashCard taken = new StashCard(List.of(third, first));
        assertThat(taken.amount()).isEqualTo(5);
        assertThat(taken.arg()).isEqualTo("vip");
        assertThat(taken.entries()).containsExactly(first, third);
    }

    @Test
    void expiryFollowsTheLoneEntry() {
        StashCard timed = new StashCard(List.of(expiring("gift", 1, null, 100, 5_000)));
        assertThat(timed.isExpired(4_999)).isFalse();
        assertThat(timed.isExpired(5_000)).isTrue();
        StashCard open = new StashCard(List.of(lasting("gift", 1, null, 100), lasting("gift", 1, null, 200)));
        assertThat(open.isExpired(Long.MAX_VALUE)).isFalse();
    }
}
