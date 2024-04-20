package Instructions;

import Microarchitecture.Microarchitecture;

public class MovLoop extends Instruction {
    private final String destination;
    private final int immediate;

    public MovLoop(int address, String destination, int immediate) {
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
        return String.format(" mov %s, %d", destination, immediate);
    }
    @Override
    public int getLatency() {
        return 1;
    }
}
