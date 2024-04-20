package Instructions;

public abstract class Multiplicative extends Producer implements DoubleConsumer {
    private final int operandB;
    private int mappedOperandB;
    Multiplicative(int address, int destination, int operandA, int operandB) {
        super(address, destination, operandA);
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
}
