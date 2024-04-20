package Instructions;

import Microarchitecture.Microarchitecture;

public abstract class Arithmetic extends Producer {

    Arithmetic(int address, int destination, int consumed) {
        super(address, destination, consumed);
    }

    @Override
    public int[] getPipelineSlots() {
        return Microarchitecture.ALU_SLOTS;
    }

    @Override
    public int getLatency() {
        return 1;
    }
}
