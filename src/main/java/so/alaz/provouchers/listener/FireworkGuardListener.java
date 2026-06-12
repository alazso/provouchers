package so.alaz.provouchers.listener;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Cancels damage from fireworks spawned as redeem effects, so a celebratory burst
 * never hurts the redeemer or bystanders.
 */
public final class FireworkGuardListener implements Listener {

    /** Marks a firework entity as a redeem effect. */
    public static final NamespacedKey EFFECT_FIREWORK = new NamespacedKey("provouchers", "effect_firework");

    @EventHandler
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
            && firework.getPersistentDataContainer().has(EFFECT_FIREWORK)) {
            event.setCancelled(true);
        }
    }
}
