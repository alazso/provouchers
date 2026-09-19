package so.alaz.provouchers.stash;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One card in the Stash menu and the stored entries behind it. Never-expiring entries of the same
 * player, voucher, and argument share one card whose amount is their sum; each expiring entry is a card
 * of its own. Claiming a card removes exactly its entries by id, never a row queued after the card was
 * built.
 *
 * @param entries the stored entries behind this card, oldest first; never empty
 */
public record StashCard(List<StashEntry> entries) {

    /** Oldest first; the id breaks ties between entries queued in the same millisecond. */
    private static final Comparator<StashEntry> OLDEST_FIRST =
        Comparator.comparingLong(StashEntry::createdAt).thenComparing(entry -> entry.id().toString());

    public StashCard {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("stash card needs at least one entry");
        }
        entries = entries.stream().sorted(OLDEST_FIRST).toList();
        Key key = Key.of(entries.getFirst());
        long total = 0;
        for (StashEntry entry : entries) {
            if (!Key.of(entry).equals(key)) {
                throw new IllegalArgumentException("stash card entries must share a player, voucher, and argument");
            }
            if (entry.expiresAt() != null && entries.size() > 1) {
                throw new IllegalArgumentException("an expiring stash entry needs a card of its own");
            }
            total += entry.amount();
        }
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("stash card amount must fit an int, was " + total);
        }
    }

    /**
     * Groups a player's entries into the cards the menu shows, ordered by each card's oldest entry. Voucher
     * ids compare ignoring case, as the registry resolves them. A group whose total would overflow an
     * {@code int} continues on a further card.
     */
    public static List<StashCard> group(Collection<StashEntry> entries) {
        List<Pile> piles = new ArrayList<>();
        Map<Key, Pile> open = new HashMap<>();
        for (StashEntry entry : entries.stream().sorted(OLDEST_FIRST).toList()) {
            if (entry.expiresAt() != null) {
                piles.add(new Pile(entry));
                continue;
            }
            Key key = Key.of(entry);
            Pile pile = open.get(key);
            if (pile != null && pile.total + entry.amount() <= Integer.MAX_VALUE) {
                pile.add(entry);
            } else {
                pile = new Pile(entry);
                open.put(key, pile);
                piles.add(pile);
            }
        }
        return piles.stream().map(pile -> new StashCard(pile.entries)).toList();
    }

    /** The voucher this card grants, as its oldest entry stores it; every entry resolves to the same voucher. */
    public String voucherId() {
        return entries.getFirst().voucherId();
    }

    /** The argument every entry on this card carries, passed to the voucher's rewards as {@code %arg%}. */
    @Nullable
    public String arg() {
        return entries.getFirst().arg();
    }

    /** How many copies this card grants: the sum of its entries' amounts. */
    public int amount() {
        int total = 0;
        for (StashEntry entry : entries) {
            total += entry.amount();
        }
        return total;
    }

    /** When this card lapses (epoch millis), or {@code null} if it never does; only a lone entry can expire. */
    @Nullable
    public Long expiresAt() {
        return entries.getFirst().expiresAt();
    }

    /** Whether this card has reached its expiry at {@code now} (epoch millis). */
    public boolean isExpired(long now) {
        return entries.getFirst().isExpired(now);
    }

    /** A card being filled while grouping, with its running total. */
    private static final class Pile {

        private final List<StashEntry> entries = new ArrayList<>();
        private long total;

        Pile(StashEntry first) {
            add(first);
        }

        void add(StashEntry entry) {
            entries.add(entry);
            total += entry.amount();
        }
    }

    /**
     * What makes never-expiring entries the same reward: the owner, the voucher (ignoring case), and the
     * argument, where {@code null} matches only {@code null}.
     */
    private record Key(UUID player, String voucherId, @Nullable String arg) {

        static Key of(StashEntry entry) {
            return new Key(entry.player(), entry.voucherId().toLowerCase(Locale.ROOT), entry.arg());
        }
    }
}
