package Instructions;

import Microarchitecture.Microarchitecture;

public class Nop extends Instruction {
    private boolean reserved = false;
    public Nop(int address) {
        super(address);
    }
    public boolean isReserved() {
        return reserved;
    }
    public void markReserved() {
        reserved = true;
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
        return reserved ? " --- " : " nop";
    }
}
