package com.globbypotato.rockhounding_core.machines.tileentity;

import java.util.Random;

import com.globbypotato.rockhounding_core.CoreItems;
import com.globbypotato.rockhounding_core.handlers.ModConfig;
import com.globbypotato.rockhounding_core.utils.CoreUtils;
import com.globbypotato.rockhounding_core.utils.FuelUtils;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyReceiver;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;

public abstract class TileEntityMachineEnergy extends TileEntityMachineInv  implements IStorage {

	public Random rand = new Random();

	public int FUEL_SLOT;

	public int cookTime = 0;
	public int recipeIndex = -1;
	public boolean activation;

	public int powerCount = 0;
	public int powerMax = 32000;
	public int redstoneCount = 0;
	public int redstoneMax = 32000;
	public int yeldCount = 0;
	public int yeldMax = 9000;
	public int chargeCount = 0;
	public int chargeMax = 1000000;

    public final EnergyStorage storage = new EnergyStorage(rfTransfer());

	public boolean permanentInductor;

	public TileEntityMachineEnergy(int inputSlots, int outputSlots, int fuelSlot) {
		super(inputSlots, outputSlots);
		this.FUEL_SLOT = fuelSlot;
	}

	//---------------- HANDLER ----------------
	@Override
	public boolean hasRF() { 
		return false; 
	}

	@Override
	public int getGUIHeight() { 
		return 0; 
	}

	public boolean isActive(){
		return this.activation;
	}

	protected boolean isRFGatedByBlend(){
		return false;
	}



	//---------------- INDUCTION ----------------
	@Override
	public boolean canInduct() {
		return hasPermanentInduction() || (!hasPermanentInduction() && (this.input.getSlots() > 0 && this.input.getStackInSlot(this.FUEL_SLOT) != null && this.input.getStackInSlot(this.FUEL_SLOT).isItemEqual(new ItemStack(CoreItems.heat_inductor))));
	}

	public boolean hasPermanentInduction() { 
		return allowPermanentInduction() && isInductionActive(); 
	}

	public boolean isInductionActive(){
		return this.permanentInductor;
	}

	public boolean allowPermanentInduction(){
		return ModConfig.allowInductor;
	}



	//---------------- FUEL ----------------
	protected void fuelHandler(ItemStack stack) {
		if(stack != null) {
			if(!hasFuelBlend() && FuelUtils.isItemFuel(stack) ){
				burnFuel(stack);
			}else if(hasFuelBlend() && ItemStack.areItemsEqual(stack, new ItemStack(CoreItems.fuel_blend))){
				burnBlend(stack);
			}else if(allowPermanentInduction() && !isInductionActive() && ItemStack.areItemsEqual(stack, new ItemStack(CoreItems.heat_inductor))){
				this.permanentInductor = true;
				this.input.setStackInSlot(this.FUEL_SLOT, null);
			}
		}
	}

	protected void burnFuel(ItemStack stack) {
		if(stack != null) {
			if( this.getPower() <= (this.getPowerMax() - FuelUtils.getItemBurnTime(stack)) ){
				this.powerCount += FuelUtils.getItemBurnTime(stack);
				stack.stackSize--;
				if(stack.stackSize <= 0){
					this.input.setStackInSlot(this.FUEL_SLOT, stack.getItem().getContainerItem(this.input.getStackInSlot(this.FUEL_SLOT)));
				}
			}
		}
	}

	public boolean isGatedPowerSource(ItemStack insertingStack){
		return (!ModConfig.enableFuelBlend && FuelUtils.isItemFuel(insertingStack) && !isFuelGated()) 
			|| CoreUtils.hasInductor(insertingStack)
			|| (ModConfig.enableFuelBlend && CoreUtils.hasBlend(insertingStack) && !isFuelGated());
	}

	public boolean isFuelGated() {
		return allowPermanentInduction() && isInductionActive() && ModConfig.gatedFuel;
	}



	//---------------- BLEND ----------------
	public boolean hasFuelBlend() { 
		return ModConfig.enableFuelBlend; 
	}

	protected void burnBlend(ItemStack stack) {
		if(stack != null) {
			if( this.getPower() <= (this.getPowerMax() - ModConfig.fuelBlendPower) ){
				this.powerCount += ModConfig.fuelBlendPower;
				stack.stackSize--;
				if(stack.stackSize <= 0){
					this.input.setStackInSlot(this.FUEL_SLOT, null);
				}
			}
		}
	}



	//---------------- REDSTONE ----------------
	protected boolean hasRedstone(ItemStack insertingStack) {
		return !hasFuelBlend()
			&& insertingStack != null 
			&& (insertingStack.getItem() == Item.getItemFromBlock(Blocks.REDSTONE_BLOCK) || insertingStack.getItem() == Items.REDSTONE);
	}

	protected void redstoneHandler(int slot, int cooktime) {
		ItemStack stack = this.input.getStackInSlot(slot);
		if(stack != null){
			if(stack.getItem() == Items.REDSTONE && this.getRedstone() <= (this.getRedstoneMax() - cooktime)){
				burnRedstone(slot, stack, cooktime);
			}else if(stack.getItem() == Item.getItemFromBlock(Blocks.REDSTONE_BLOCK) && this.getRedstone() <= (this.getRedstoneMax() - (cooktime * 9))){
				burnRedstone(slot, stack, cooktime * 9);
			}
		}
	}

	private void burnRedstone(int slot, ItemStack stack, int charge) {
		this.redstoneCount += charge; 
		stack.stackSize--;
		if(stack.stackSize <= 0){
			this.input.setStackInSlot(slot, null);
		}
	}

	public boolean isRedstoneRequired(int amount){
		return hasFuelBlend() && isRFGatedByBlend() ? true : this.getRedstone() >= amount;
	}

	public boolean isRedstoneRefilled(){
		return hasFuelBlend() && isRFGatedByBlend() ? true : isFullRedstone();
	}

	public boolean redstoneIsRefillable() {
		return hasRF() && (!hasFuelBlend() || (hasFuelBlend() && !isRFGatedByBlend()));
	}

	public boolean canRefillOnlyPower() {
		return canInduct() && !hasRF();
	}

	public boolean isRedstoneFilled() {
		return canInduct() && hasRF() && isRedstoneRefilled();
	}



	//---------------- I/O ----------------
	@Override
	public void readFromNBT(NBTTagCompound compound){
		super.readFromNBT(compound);
		this.powerCount = compound.getInteger("PowerCount");
		this.redstoneCount = compound.getInteger("RedstoneCount");
		this.cookTime = compound.getInteger("CookTime");
		this.permanentInductor = compound.getBoolean("Inductor");
		this.activation = compound.getBoolean("Activation");
		this.storage.readFromNBT(compound.getCompoundTag("StorageNBT"));

	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound){
		super.writeToNBT(compound);
		compound.setInteger("PowerCount", this.powerCount);
		compound.setInteger("RedstoneCount", this.redstoneCount);
		compound.setInteger("CookTime", this.cookTime);
		compound.setBoolean("Inductor", isInductionActive());
		compound.setBoolean("Activation", isActive());

		NBTTagCompound stotageNBT = new NBTTagCompound();
		this.storage.writeToNBT(stotageNBT);
		compound.setTag("StorageNBT", stotageNBT);

		return compound;
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		if (capability == CapabilityEnergy.ENERGY) {
			return true;
		}
		return super.hasCapability(capability, facing);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if ((capability == CapabilityEnergy.ENERGY)) {
			return (T)CapabilityEnergy.ENERGY.cast(this);
		}
		return super.getCapability(capability, facing);
	}



	//---------------- COFH ----------------
	@Override
	public int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate) {
		return this.storage.receiveEnergy(maxReceive, simulate);
	}

	//---------------- FORGE ----------------
	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		return this.storage.receiveEnergy(maxReceive, simulate);
	}



	//---------------- VARIABLES ----------------
	@Override
	public int getPower() { 	  return this.powerCount; }
	@Override
	public int getPowerMax() { 	  return this.powerMax; }
	@Override
	public int getRedstone() { 	  return this.redstoneCount; }
	@Override
	public int getRedstoneMax() { return this.redstoneMax; }
	@Override
	public int getChargeMax() {   return this.chargeMax; }
	@Override
	public int getYeld() { 	  	  return this.yeldCount; }
	@Override
	public int getYeldMax() {     return this.yeldMax; }

	public int rfTransfer(){
    	return 200;
    }



	//---------------- PROCESS ----------------
	public void acceptEnergy() {
		int energyReceived = 0;
		if(isRedstoneFilled() || canRefillOnlyPower() ){
	        if(!isFullPower()){
		        energyReceived = Math.min(this.getPowerMax() - this.getPower(), this.storage.getEnergyStored());
		        this.powerCount += energyReceived;
	        	this.storage.extractEnergy(energyReceived, false);
	        }else{
				this.powerCount = this.getPowerMax();
			}
		}else if(redstoneIsRefillable()){
			if(!isFullRedstone()){
		        energyReceived = Math.min(this.getRedstoneMax() - this.getRedstone(), this.storage.getEnergyStored());
	        	this.redstoneCount += energyReceived;
	        	this.storage.extractEnergy(energyReceived, false);
			}else{
				this.redstoneCount = this.getRedstoneMax();
			}
		}
	}

	public void sendEnergy(TileEntity checkTile, EnumFacing facing){
		int possibleEnergy = 0;
		if(checkTile.hasCapability(CapabilityEnergy.ENERGY, facing.getOpposite()) && checkTile.getCapability(CapabilityEnergy.ENERGY, facing.getOpposite()).canReceive()){
			possibleEnergy = checkTile.getCapability(CapabilityEnergy.ENERGY, facing.getOpposite()).receiveEnergy(rfTransfer(), true);
			if(possibleEnergy > 0){
				checkTile.getCapability(CapabilityEnergy.ENERGY, facing.getOpposite()).receiveEnergy(possibleEnergy, false);
				this.redstoneCount -= possibleEnergy;
			}
		}else{
			if(checkTile instanceof IEnergyReceiver) {
				IEnergyReceiver te = (IEnergyReceiver) checkTile;
				if(te.canConnectEnergy(facing)){
					possibleEnergy = te.receiveEnergy(facing.getOpposite(), rfTransfer(), true);
					if(possibleEnergy > 0){
						te.receiveEnergy(facing.getOpposite(), possibleEnergy, false);
						this.redstoneCount -= possibleEnergy;
					}
				}
			}
		}
	}

}