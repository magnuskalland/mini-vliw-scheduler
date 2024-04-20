package Instructions;

import Microarchitecture.Microarchitecture;

public class Mulu extends Multiplicative {
    public Mulu(int address, int destination, int operandA, int operandB) {
        super(address, destination, operandA, operandB);
    }

    @Override
    public int[] getPipelineSlots() {
        return Microarchitecture.MUL_SLOTS;
    }

    @Override
    public String toString() {
        return String.format("%s mulu x%d, x%d, x%d",
                getPredicateString(), getMappedDestination(), getMappedOperandA(), getMappedOperandB());
    }

    @Override
    public int getLatency() {
        return 3;
    }
}
