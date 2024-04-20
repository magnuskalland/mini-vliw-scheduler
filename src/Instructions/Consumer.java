package Instructions;

public abstract class Consumer extends Instruction {
    private final int consumed;
    private int mappedConsumed;
    private boolean remapped;
    Consumer(int address, int consumed) {
        super(address);
        this.consumed = consumed;
        this.mappedConsumed = consumed;
        this.remapped = false;
    }
    public int getOperandA() {
        return consumed;
    }
    public int getMappedOperandA() {
        return mappedConsumed;
    }
    public void setOperandA(int operand) {
        mappedConsumed = operand;
        remapped = true;
    }
    public boolean isOperandARemapped() {
        return remapped;
    }
    public boolean isDependentOn(Producer other) {
        if (other.getDestination() == getOperandA())
            return true;
        if (!(this instanceof DoubleConsumer))
            return false;
        return other.getDestination() == ((DoubleConsumer) this).getOperandB();
    }

}
