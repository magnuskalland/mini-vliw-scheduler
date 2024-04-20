package Instructions;

public class Sub extends Arithmetic implements DoubleConsumer {
    private final int operandB;
    private int mappedOperandB;
    public Sub(int address, int destination, int consumed, int operandB) {
        super(address, destination, consumed);
        this.operandB = operandB;
        this.mappedOperandB = operandB;
    }

    @Override
    public int getOperandB() {
        return operandB;
    }

    @Override
    public void setOperandB(int operand) {
        this.mappedOperandB = operand;
    }

    @Override
    public int getMappedOperandB() {
        return mappedOperandB;
    }

    @Override
    public String toString() {
        return String.format("%s sub x%d, x%d, x%d",
                getPredicateString(), getMappedDestination(), getMappedOperandA(), getMappedOperandB());
    }
}
