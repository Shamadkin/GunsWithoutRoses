package lykrast.gunswithoutroses;

import lykrast.gunswithoutroses.items.BulletItem;
import lykrast.gunswithoutroses.items.GunItem;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.IForgeRegistry;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = GunsWithoutRoses.MODID)
public class GunsWithoutRosesItems {
	public static GunItem ironGun, goldGun;
	public static BulletItem flintBullet, ironBullet, blazeBullet, hungerBullet;

	@SubscribeEvent
	public static void registerItems(final RegistryEvent.Register<Item> event) {
		IForgeRegistry<Item> reg = event.getRegistry();
		
		ironGun = initItem(reg, new GunItem(defP().maxDamage(813)), "iron_gun");
		goldGun = initItem(reg, new GunItem(defP().maxDamage(104)), "gold_gun");
		
		flintBullet = initItem(reg, new BulletItem(defP()), "flint_bullet");
		ironBullet = initItem(reg, new BulletItem(defP()), "iron_bullet");
		blazeBullet = initItem(reg, new BulletItem(defP()), "blaze_bullet");
		hungerBullet = initItem(reg, new BulletItem(defP().maxStackSize(1)), "hunger_bullet");
	}

	public static Item.Properties defP() {
		return new Item.Properties().group(ItemGroupGunsWithoutRoses.INSTANCE);
	}

	public static <I extends Item> I initItem(IForgeRegistry<Item> reg, I item, String name) {
		item.setRegistryName(GunsWithoutRoses.MODID, name);
		reg.register(item);
		return item;
	}
}
