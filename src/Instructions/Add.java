package Instructions;

public class Add extends Arithmetic implements DoubleConsumer {
    private final int operandB;
    private int mappedOperandB;
    private boolean operandBRemapped;
    public Add(int address, int destination, int consumed, int operandB) {
        super(address, destination, consumed);
        this.operandB = operandB;
        this.operandBRemapped = false;
    }

    @Override
    public int getOperandB() {
        return operandB;
    }

    @Override
    public void setOperandB(int operand) {
        this.mappedOperandB = operand;
        operandBRemapped = true;
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
    public String toString() {
        return String.format("%s add x%d, x%d, x%d",
                getPredicateString(), getMappedDestination(), getMappedOperandA(), getMappedOperandB());
    }
}
