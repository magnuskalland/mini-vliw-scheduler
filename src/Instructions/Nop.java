package Instructions;

import Microarchitecture.Microarchitecture;

public class Nop extends Instruction {
    private boolean reserved = false;
    private final int slotIndex;
    public Nop(int address, int slotIndex) {
        super(address);
        this.slotIndex = slotIndex;
    }
    public boolean isReserved() {
        return reserved;
    }
    public void setReserved() {
        reserved = true;
    }
    public int getSlotIndex() {
        return slotIndex;
    }
    @Override
    public int getLatency() {
        return 1;
    }
    @Override
    public int[] getPipelineSlots() {
        return Microarchitecture.NOP_SLOTS;
    }
    @Override
    public String toString() {
        return " nop";
    }
}
