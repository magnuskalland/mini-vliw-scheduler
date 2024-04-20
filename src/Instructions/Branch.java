package Instructions;

import Microarchitecture.Microarchitecture;

public abstract class Branch extends Instruction {
    private int target;
    Branch(int address, int target) {
        super(address);
        this.target = target;
    }
    public int getTarget() {
        return target;
    }
    public void setTarget(int address) {
        target = address;
    }
    @Override
    public int getLatency() {
        return 1;
    }
    @Override
    public int[] getPipelineSlots() {
        return Microarchitecture.BR_SLOTS;
    }
}
