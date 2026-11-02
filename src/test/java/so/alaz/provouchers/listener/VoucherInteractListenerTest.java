package so.alaz.provouchers.listener;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which entities a held voucher is kept from acting on. */
class VoucherInteractListenerTest {

    @Test
    void coversMobsAndArmorStandsWhileTheSettingIsOn() {
        assertThat(VoucherInteractListener.blocksUseOn(entity(ArmorStand.class), true)).isTrue();
        assertThat(VoucherInteractListener.blocksUseOn(entity(Cow.class), true)).isTrue();
        assertThat(VoucherInteractListener.blocksUseOn(entity(Villager.class), true)).isTrue();
    }

    @Test
    void leavesThemAloneWhileTheSettingIsOff() {
        assertThat(VoucherInteractListener.blocksUseOn(entity(ArmorStand.class), false)).isFalse();
        assertThat(VoucherInteractListener.blocksUseOn(entity(Cow.class), false)).isFalse();
    }

    @Test
    void alwaysCoversItemFrames() {
        assertThat(VoucherInteractListener.blocksUseOn(entity(ItemFrame.class), true)).isTrue();
        assertThat(VoucherInteractListener.blocksUseOn(entity(ItemFrame.class), false)).isTrue();
    }

    @Test
    void neverCoversPlayersSoPlayerTypeNpcsStayClickable() {
        assertThat(VoucherInteractListener.blocksUseOn(entity(Player.class), true)).isFalse();
        assertThat(VoucherInteractListener.blocksUseOn(entity(Player.class), false)).isFalse();
    }

    /**
     * The server fires {@link PlayerInteractAtEntityEvent} for an entity click, and the listener registers
     * for its parent. That only works while the subclass shares the parent's handler list.
     */
    @Test
    void theEntityClickEventSharesItsParentsHandlerList() {
        assertThatThrownBy(() -> PlayerInteractAtEntityEvent.class.getDeclaredMethod("getHandlerList"))
            .isInstanceOf(NoSuchMethodException.class);
    }

    private static <T extends Entity> T entity(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> null));
    }
}
