package Instructions;

import Microarchitecture.Microarchitecture;

public class MovP extends Instruction {
    private final int destination;
    private final String immediate;

    public MovP(int address, int destination, String immediate) {
        super(address);
        this.destination = destination;
        this.immediate = immediate;
    }

    @Override
    public int[] getPipelineSlots() {
        return Microarchitecture.ALU_SLOTS;
    }

    @Override
    public String toString() {
        return String.format(" mov p%d, %s", destination, immediate);
    }

    @Override
    public int getLatency() {
        return 1;
    }
}
