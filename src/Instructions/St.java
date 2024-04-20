package Instructions;

import Microarchitecture.Microarchitecture;

public class St extends Consumer implements Predicateable, Memory, DoubleConsumer {
    private final int offset;
    private final int operandB;
    private int mappedOperandB;
    private boolean operandBRemapped;
    protected Integer predicate = null;

    public St(int address, int consumed, int offset, int memoryDestination) {
        super(address, consumed);
        this.offset = offset;
        this.operandB = memoryDestination;
        this.mappedOperandB = operandB;
        this.operandBRemapped = false;
    }

    @Override
    public int[] getPipelineSlots() {
        return Microarchitecture.MEM_SLOTS;
    }

    @Override
    public String toString() {
        return String.format("%s st x%d, %d(x%d)",
                getPredicateString(), getMappedOperandA(), getOffset(), getMappedOperandB());
    }

    @Override
    public int getLatency() {
        return 1;
    }
    @Override
    public int getOperandB() {
        return operandB;
    }
    @Override
    public void setOperandB(int operand) {
        this.mappedOperandB = operand;
        this.operandBRemapped = true;
    }
    @Override
    public int getMappedOperandB() {
        return mappedOperandB;
    }
    @Override
    public boolean isOperandBRemapped() {
        return operandBRemapped;
    }
    @Override
    public int getOffset() {
        return offset;
    }
    @Override
    public void setPredicate(int predicate) {
        this.predicate = predicate;
    }
    @Override
    public String getPredicateString() {
        return predicate == null ? "" : String.format("(p%d)", predicate);
    }
}
