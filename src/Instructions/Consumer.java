package Instructions;

public abstract class Consumer extends Instruction {
    private final int consumed;
    private int mappedConsumed;
    Consumer(int address, int consumed) {
        super(address);
        this.consumed = consumed;
        this.mappedConsumed = consumed;
    }
    public int getOperandA() {
        return consumed;
    }
    public int getMappedOperandA() {
        return mappedConsumed;
    }
    public void setOperandA(int operand) {
        mappedConsumed = operand;
    }
    public boolean isDependentOn(Producer other) {
        if (other.getDestination() == getOperandA())
            return true;
        if (!(this instanceof DoubleConsumer))
            return false;
        return other.getDestination() == ((DoubleConsumer) this).getOperandB();
    }

}
