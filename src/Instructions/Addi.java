package Instructions;

public class Addi extends Arithmetic implements Immediate {
    private final String immediate;
    public Addi(int address, int destination, int consumed, String immediate) {
        super(address, destination, consumed);
        this.immediate = immediate;
    }
    @Override
    public String toString() {
        return String.format("%s addi x%d, x%d, %s",
                getPredicateString(), getMappedDestination(), getMappedOperandA(), getImmediate());
    }

    @Override
    public String getImmediate() {
        return immediate;
    }
}
