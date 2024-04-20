package Instructions;

public class Mov extends Arithmetic {
    public Mov(int address, int destination, int consumed) {
        super(address, destination, consumed);
    }
    @Override
    public String toString() {
        return String.format("%s mov x%d, x%d", getPredicateString(), getMappedDestination(), getMappedOperandA());
    }
}
