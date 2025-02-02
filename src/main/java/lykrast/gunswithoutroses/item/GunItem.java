package lykrast.gunswithoutroses.item;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import lykrast.gunswithoutroses.entity.BulletEntity;
import lykrast.gunswithoutroses.registry.ModEnchantments;
import lykrast.gunswithoutroses.registry.ModItems;
import lykrast.gunswithoutroses.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class GunItem extends ProjectileWeaponItem {
	protected int bonusDamage;
	protected double damageMultiplier;
	protected int fireDelay;
	protected double inaccuracy;
	protected double projectileSpeed = 3;
	private int enchantability;
	protected boolean ignoreInvulnerability = false;
	protected double chanceFreeShot = 0;
	protected SoundEvent fireSound = ModSounds.gun;
	//Hey guess what if I just put the repair material it crashes... so well let's do like vanilla and just use a supplier
	protected Supplier<Ingredient> repairMaterial;

	public GunItem(Properties properties, int bonusDamage, double damageMultiplier, int fireDelay, double inaccuracy, int enchantability) {
		super(properties);
		this.bonusDamage = bonusDamage;
		this.damageMultiplier = damageMultiplier;
		this.enchantability = enchantability;
		this.fireDelay = fireDelay;
		this.inaccuracy = inaccuracy;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		//"Oh yeah I will use the vanilla method so that quivers can do their thing"
		//guess what the quivers suck
		ItemStack ammo = player.getProjectile(gun);

		if (!ammo.isEmpty() || player.getAbilities().instabuild) {
			if (ammo.isEmpty()) ammo = new ItemStack(ModItems.flintBullet);

			IBullet bulletItem = (IBullet) (ammo.getItem() instanceof IBullet ? ammo.getItem() : ModItems.flintBullet);
			if (!world.isClientSide) {
				boolean bulletFree = player.getAbilities().instabuild || !shouldConsumeAmmo(world, gun, player);
				
				//Workaround for quivers not respecting getAmmoPredicate()
				ItemStack shotAmmo = ammo.getItem() instanceof IBullet ? ammo : new ItemStack(ModItems.flintBullet);
				shoot(world, player, gun, shotAmmo, bulletItem, bulletFree);
				
				gun.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));
				if (!bulletFree) bulletItem.consume(ammo, player);
			}

			world.playSound(null, player.getX(), player.getY(), player.getZ(), fireSound, SoundSource.PLAYERS, 1.0F, world.getRandom().nextFloat() * 0.4F + 0.8F);
			player.awardStat(Stats.ITEM_USED.get(this));

			player.getCooldowns().addCooldown(this, getFireDelay(gun, player));
			return InteractionResultHolder.consume(gun);
		}
		else return InteractionResultHolder.fail(gun);
	}
	
	/**
	 * Fires one shot after all the checks have passed. You can actually fire whatever you want here.<br>
	 * Ammo is consumed afterwards, we're only shooting the bullet(s) here.
	 * @param world world
	 * @param player Player that shoots
	 * @param gun Gun shooting
	 * @param ammo Ammo being shot
	 * @param bulletItem IBullet used for the shot, may not match the ammo
	 * @param bulletFree true if no ammo was actually consumed (creative or Preserving enchant for example)
	 */
	protected void shoot(Level world, Player player, ItemStack gun, ItemStack ammo, IBullet bulletItem, boolean bulletFree) {
		BulletEntity shot = bulletItem.createProjectile(world, ammo, player);
		shot.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, (float)getProjectileSpeed(gun, player), (float)getInaccuracy(gun, player));
		shot.setDamage((shot.getDamage() + getBonusDamage(gun, player)) * getDamageMultiplier(gun, player));
		shot.setIgnoreInvulnerability(ignoreInvulnerability);
		changeBullet(world, player, gun, shot, bulletFree);

		world.addFreshEntity(shot);
	}
	
	/**
	 * Lets the gun do custom stuff to default bullets without redoing all the base stuff from shoot.
	 */
	protected void changeBullet(Level world, Player player, ItemStack gun, BulletEntity bullet, boolean bulletFree) {
		
	}
	
	/**
	 * Rolls chance to know if ammo should be consumed for the shot. Applies both the baseline chance and Preserving enchantment.<br>
	 * If you change this don't forget to tweak getInverseChanceFreeShot accordingly for the tooltip (and call super).
	 */
	public boolean shouldConsumeAmmo(Level world, ItemStack stack, Player player) {
		if (chanceFreeShot > 0 && world.getRandom().nextDouble() < chanceFreeShot) return false;
		
		int preserving = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.preserving, stack);
		//(level) in (level + 2) chance to not consume
		if (preserving >= 1 && world.getRandom().nextInt(preserving + 2) >= 2) return false;
		
		return true;
	}

	/**
	 * Gets the flat bonus damage (applied BEFORE the multiplier). This takes into account Impact enchantment.
	 */
	public double getBonusDamage(ItemStack stack, @Nullable Player player) {
		int impact = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.impact, stack);
		return bonusDamage + (impact >= 1 ? 0.5 * (impact + 1) : 0);
	}
	
	public double getDamageMultiplier(ItemStack stack, @Nullable Player player) {
		return damageMultiplier;
	}
	
	/**
	 * Gets the min time in ticks between 2 shots. This takes into account Sleight of Hand enchantment.
	 */
	public int getFireDelay(ItemStack stack, @Nullable Player player) {
		return Math.max(1, fireDelay - (int)(fireDelay * EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.sleightOfHand, stack) * 0.15));
	}
	
	/**
	 * Checks if the gun has baseline perfect accuracy.<br>
	 * Used for tooltip and for Bullseye (which can't be applied since it would do nothing).
	 */
	public boolean hasPerfectAccuracy() {
		return inaccuracy <= 0;
	}
	
	/**
	 * Gets the inaccuracy, taking into account Bullseye enchantment.<br>
	 * Accuracy is actually inarccuracy internally, because it's easier to math.<br>
	 * The formula is just accuracy = 1 / inaccuracy.
	 */
	public double getInaccuracy(ItemStack stack, @Nullable Player player) {
		return Math.max(0, inaccuracy / (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.bullseye, stack) + 1.0));
	}
	
	public double getProjectileSpeed(ItemStack stack, @Nullable Player player) {
		//I wanted to follow kat's suggestion and make bullseye for snipers increase projectile speed
		//But high projectile speed cause weird "snapping" issues on bullets
		return projectileSpeed;
	}
	
	/**
	 * Chance to actually CONSUME ammo, to properly multiply probabilities together.<br>
	 * Tooltip then does the math to display it nicely.
	 */
	public double getInverseChanceFreeShot(ItemStack stack, @Nullable Player player) {
		double chance = 1 - chanceFreeShot;
		int preserving = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.preserving, stack);
		if (preserving >= 1) chance *= 2.0/(preserving + 2);
		return chance;
	}
	
	/**
	 * Says if the damage is changed from base value. Used for tooltip.
	 */
	protected boolean isDamageModified(ItemStack stack) {
		return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.impact, stack) >= 1;
	}
	
	/**
	 * Says if the fire delay is changed from base value. Used for tooltip.
	 */
	protected boolean isFireDelayModified(ItemStack stack) {
		return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.sleightOfHand, stack) >= 1;
	}
	
	/**
	 * Says if the (in)accuracy is changed from base value. Used for tooltip.
	 */
	protected boolean isInaccuracyModified(ItemStack stack) {
		return !hasPerfectAccuracy() && EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.bullseye, stack) >= 1;
	}
	
	/**
	 * Says if the chance for free shots is changed from base value. Used for tooltip.
	 */
	protected boolean isChanceFreeShotModified(ItemStack stack) {
		return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.preserving, stack) >= 1;
	}

	/**
	 * Sets whether the bullets ignore invulnerability frame (default no), used when making the item for registering.
	 */
	public GunItem ignoreInvulnerability(boolean ignoreInvulnerability) {
		this.ignoreInvulnerability = ignoreInvulnerability;
		return this;
	}

	/**
	 * Sets a chance to NOT consume ammo, used when making the item for registering.
	 */
	public GunItem chanceFreeShot(double chanceFreeShot) {
		this.chanceFreeShot = chanceFreeShot;
		return this;
	}
	
	/**
	 * Sets the firing sound, used when making the item for registering.
	 */
	public GunItem fireSound(SoundEvent fireSound) {
		this.fireSound = fireSound;
		return this;
	}

	/**
	 * Sets a projectile speed, used when making the item for registering.<br>
	 * Base value is 3. High values (like 5-6) cause weird behavior so don't with the base bullets.
	 */
	public GunItem projectileSpeed(double projectileSpeed) {
		this.projectileSpeed = projectileSpeed;
		return this;
	}
	
	/**
	 * Sets the repair material, used when making the item for registering.
	 */
	public GunItem repair(Supplier<Ingredient> repairMaterial) {
		this.repairMaterial = repairMaterial;
		return this;
	}
	
	@Override
	public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
		//Disallow Bullseye if the gun has perfect accuracy
		if (enchantment == ModEnchantments.bullseye && hasPerfectAccuracy()) return false;
		return super.canApplyAtEnchantingTable(stack, enchantment);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flagIn) {
		if (Screen.hasShiftDown()) {
			//Damage
			double damageMultiplier = getDamageMultiplier(stack, null);
			double damageBonus = getBonusDamage(stack, null) * damageMultiplier;
			
			if (damageMultiplier != 1) {
				if (damageBonus != 0) tooltip.add(new TranslatableComponent("tooltip.gunswithoutroses.gun.damage.both" + (isDamageModified(stack) ? ".modified" : ""), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(damageMultiplier), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(damageBonus)));
				else tooltip.add(new TranslatableComponent("tooltip.gunswithoutroses.gun.damage.mult" + (isDamageModified(stack) ? ".modified" : ""), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(damageMultiplier)));
			}
			else if (damageBonus != 0) tooltip.add(new TranslatableComponent("tooltip.gunswithoutroses.gun.damage.flat" + (isDamageModified(stack) ? ".modified" : ""), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(damageBonus)));
			
			//Fire rate
			int fireDelay = getFireDelay(stack, null);
			tooltip.add(new TranslatableComponent("tooltip.gunswithoutroses.gun.firerate" + (isFireDelayModified(stack) ? ".modified" : ""), fireDelay, (60*20) / fireDelay));
			
			//Accuracy
			double inaccuracy = getInaccuracy(stack, null);
			if (inaccuracy <= 0) tooltip.add(new TranslatableComponent("tooltip.gunswithoutroses.gun.accuracy.perfect" + (isInaccuracyModified(stack) ? ".modified" : "")));
			else tooltip.add(new TranslatableComponent("tooltip.gunswithoutroses.gun.accuracy" + (isInaccuracyModified(stack) ? ".modified" : ""), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(1.0 / inaccuracy)));
			
			//Chance to not consume ammo
			double inverseChanceFree = getInverseChanceFreeShot(stack, null);
			if (inverseChanceFree < 1) tooltip.add(new TranslatableComponent("tooltip.gunswithoutroses.gun.chance_free" + (isChanceFreeShotModified(stack) ? ".modified" : ""), (int)((1 - inverseChanceFree) * 100)));
			
			//Other stats
			if (ignoreInvulnerability) tooltip.add(new TranslatableComponent("tooltip.gunswithoutroses.gun.ignore_invulnerability").withStyle(ChatFormatting.GRAY));
			
			addExtraStatsTooltip(stack, world, tooltip);
		}
		else tooltip.add(new TranslatableComponent("tooltip.gunswithoutroses.shift"));
	}
	
	/**
	 * Add more tooltips that will be displayed below the base stats.
	 */
	protected void addExtraStatsTooltip(ItemStack stack, @Nullable Level world, List<Component> tooltip) {
		
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public int getEnchantmentValue() {
		return enchantability;
	}

	//TODO ammo types
	private static final Predicate<ItemStack> BULLETS = (stack) -> stack.getItem() instanceof IBullet && ((IBullet)stack.getItem()).hasAmmo(stack);

	@Override
	public Predicate<ItemStack> getAllSupportedProjectiles() {
		return BULLETS;
	}

	@Override
	public int getDefaultProjectileRange() {
		// No idea what this is yet, so using the Bow value (Crossbow is 8)
		return 15;
	}
	
	@Override
	public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
		return (repairMaterial != null && repairMaterial.get().test(repair)) || super.isValidRepairItem(toRepair, repair);
	}
	
	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		return !ItemStack.isSameIgnoreDurability(oldStack, newStack);
	}

}
