package com.bergerkiller.bukkit.tc;

import java.util.HashSet;

import net.minecraft.server.Entity;
import net.minecraft.server.EntityMinecart;
import net.minecraft.server.World;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Minecart;
import org.bukkit.material.Rails;

public class MinecartMember extends NativeMinecartMember {
	public MinecartMember(World world, double d0, double d1, double d2, int i) {
		super(world, d0, d1, d2, i);
	}
	
	
	/*
	 * Overridden Minecart physics function, split up in bits.
	 */
	@Override
	public void m_() {
		MinecartGroup g = this.getGroup();
		if (g != null) {
			if (g.tail() == this) {
				for (MinecartMember m : g.getMembers()) {
					m.fixYaw();
					m.preUpdate();
				}
				g.update();
				for (MinecartMember m : g.getMembers()) {
					m.postUpdate(m.forceFactor);
				}
			}
		} else {
			super.m_();
		}
	}

	private static HashSet<MinecartMember> replacedCarts = new HashSet<MinecartMember>();

	public static MinecartMember get(Minecart m) {
		EntityMinecart em = Util.getNative(m);
		if (em instanceof MinecartMember) return (MinecartMember) em;
		return null;
	}
	public static MinecartMember get(Minecart m, MinecartGroup group) {
		//to prevent unneeded cart conversions
		EntityMinecart em = Util.getNative(m);
		if (em instanceof MinecartMember) {
			MinecartMember mm = (MinecartMember) em;
			mm.group = group;
			return mm;
		}
				
		//declare a new MinecartFixer with the same characteristics as the previous Minecart
		MinecartMember f = new MinecartMember(em.world, em.lastX, em.lastY, em.lastZ, em.type);
		f.yaw = em.yaw;
		f.pitch = em.pitch;
		f.locX = em.locX;
		f.locY = em.locY;
		f.locZ = em.locZ;
		f.motX = em.motX;
		f.motY = em.motY;
		f.motZ = em.motZ;
		f.uniqueId = em.uniqueId;
		f.group = group;
		Entity passenger = em.passenger;

		//Swap
		m.remove();
		f.world.addEntity(f);
		
		//Teleport passenger over
		if (passenger != null) passenger.setPassengerOf(f);
		
		replacedCarts.add(f);
		return f;
	}
	public static void markRemoved(MinecartMember mm) {
		replacedCarts.remove((MinecartMember) mm);
	}
	public static void undoReplacement(MinecartMember mm) {
		if (!mm.dead) {
			EntityMinecart em = new EntityMinecart(mm.world, mm.lastX, mm.lastY, mm.lastZ, mm.type);
			em.yaw = mm.yaw;
			em.pitch = mm.pitch;
			em.locX = mm.locX;
			em.locY = mm.locY;
			em.locZ = mm.locZ;
			em.motX = mm.motX;
			em.motY = mm.motY;
			em.motZ = mm.motZ;
			em.uniqueId = mm.uniqueId;
			em.world.removeEntity(mm);
			em.world.addEntity(em);
			if (mm.passenger != null)
				mm.passenger.setPassengerOf(em);
		}
		replacedCarts.remove(mm);
	}
	public static void undoReplacement() {
		for (MinecartMember m : replacedCarts.toArray(new MinecartMember[0])) {
			undoReplacement(m);
		}
	}
	
	public static boolean isMember(Minecart m) {
		return Util.getNative(m) instanceof MinecartMember;
	}
		
	private double forceFactor = 1;
	private float customYaw = 0;
	private MinecartGroup group;

 	public Minecart getMinecart() {
		return (Minecart) this.getBukkitEntity();
	}
 	public MinecartGroup getGroup() {
 		return this.group;
 	}
 	public void setGroup(MinecartGroup group) {
 		this.group = group;
 	}
	
 	public static boolean validate(Minecart m) {
 		return !(m.isDead() || (TrainCarts.removeDerailedCarts && isDerailed(m)));
 	}
 	public static boolean validate(MinecartMember mm) {
 		return !(mm.dead || (TrainCarts.removeDerailedCarts && mm.isDerailed()));
 	}
 		
	public static boolean isDerailed(Minecart m) {
		if (m == null) return true;
		MinecartMember mm = get(m);
		if (mm == null) {
			return Util.getRailsBlock(m) == null;
		} else {
			return isDerailed(mm);
		}
	}
	public static boolean isDerailed(MinecartMember mm) {
		if (mm == null) return true;
		return mm.getRailsBlock() == null;
	}	
	public boolean isDerailed() {
		return isDerailed(this);
	}
	

	public Block getRailsBlock() {
		if (this.group != null) {
			if (super.isDerailed()) return null;
		}
		return Util.getRailsBlock(this.getMinecart());
	}
	public Rails getRails() {
		return Util.getRails(this.getRailsBlock());
	}
		
	/*
	 * Velocity functions
	 */
	public void stop() {
		this.motX = 0;
		this.motY = 0;
		this.motZ = 0;
	}
	public double getForce() {
		double force = Math.sqrt(motX * motX + motZ * motZ);
		if (getForwardForce() < 0) force = -force;
		return force;
	}
	public double getForwardForce() {
		double ryaw = -this.getYaw() / 180 * Math.PI;
        return Math.cos(ryaw) * this.motZ + Math.sin(ryaw) * this.motX;
	}

	public void setForwardForce(double force) {
		if (isTurned()) {
			double l = Math.sqrt(motX * motX + motZ * motZ);
			if (l > 0.001) {
				double factor = force / l;
				this.motX *= factor;
				this.motZ *= factor;
				return;
			}
		}
		double ryaw = -getYaw() / 180 * Math.PI;
		this.motX = Math.sin(ryaw) * force;
		this.motZ = Math.cos(ryaw) * force;
	}
	public void addForceFactor(double forcer, double factor) {
		this.forceFactor = 1 + (forcer * factor);
	}
	
	/*
	 * Location functions
	 */
	public org.bukkit.World getWorld() {
		return world.getWorld();
	}
	public Location getLocation() {
		return new Location(getWorld(), getX(), getY(), getZ(), getYaw(), getPitch());
	}
	public double getSubX() {
		double x = getX() + 0.5;
		return x - (int) x;
	}	
	public double getSubZ() {
		double z = getZ() + 0.5;
		return z - (int) z;
	}
	
	public double distanceXZ(MinecartMember m) {
		return Util.distance(this.getX(), this.getZ(), m.getX(), m.getZ());
	}
	public double distance(MinecartMember m) {
		return Util.distance(this.getX(), this.getY(), this.getZ(), m.getX(), m.getY(), m.getZ());
	}
	
	/*
	 * Pitch functions
	 */
	public float getPitch() {
		return this.pitch;
	}
	public float getPitchDifference(MinecartMember comparer) {
		return getPitchDifference(comparer.getPitch());
	}
	public float getPitchDifference(float pitchcomparer) {
		return Util.getAngleDifference(getPitch(), pitchcomparer);
	}
	
	
	/*
	 * Yaw functions
	 */
	public float getYaw() {
		return this.customYaw;
	}
	public float getYawDifference(float yawcomparer) {
		return Util.getAngleDifference(customYaw, yawcomparer);
	}	
	public float setYaw(float yawcomparer) {
		customYaw = 0;
		double x = getSubX();
		double z = getSubZ();
		if (x == 0 && Math.abs(motX) < 0.001) {
			//cart is driving along the x-axis
			customYaw = 0;
		} else if (z == 0 && Math.abs(motZ) < 0.001) {
			//cart is driving along the z-axis
			customYaw = 90;
		} else {
			//try to get the yaw from the rails
			customYaw = Util.getRailsYaw(getRails());
		}
		//Fine tuning
		if (getYawDifference(yawcomparer) > 90) customYaw += 180;
		return customYaw;
	}
	public float setYawTo(MinecartMember head) {
		return setYaw(Util.getLookAtYaw(this, head));
	}
	public float setYawFrom(MinecartMember tail) {
		return setYaw(Util.getLookAtYaw(tail, this));
	}

	/*
	 * States
	 */
	public boolean isInLoadedChunks() {
		return Util.getChunkSafe(getLocation());
	}
	public boolean isMoving() {
		if (motX > 0.001) return true;
		if (motX < -0.001) return true;
		if (motZ > 0.001) return true;
		if (motZ < -0.001) return true;
		return false;
	}
	public boolean isTurned() {
		return getSubX() != 0 && getSubZ() != 0;
	}

}