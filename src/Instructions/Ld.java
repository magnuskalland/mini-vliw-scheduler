package Instructions;

import Microarchitecture.Microarchitecture;

public class Ld extends Producer implements Memory {
    private final int offset;
    public Ld(int address, int destination, String offset, int consumed) {
        super(address, destination, consumed);
        this.offset = parseImmediate(offset);
    }

    @Override
    public int[] getPipelineSlots() {
        return Microarchitecture.MEM_SLOTS;
    }

    @Override
    public String toString() {
        return String.format("%s ld x%d, %d(x%d)",
                getPredicateString(), getMappedDestination(), getOffset(), getMappedOperandA());
    }

    @Override
    public int getLatency() {
        return 1;
    }

    @Override
    public int getOffset() {
        return offset;
    }
}
