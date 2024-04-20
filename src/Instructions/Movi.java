package Instructions;

public class Movi extends Arithmetic implements Immediate {
    private final String immediate;

    public Movi(int address, int destination, String immediate) {
        super(address, destination, -1);
        this.immediate = immediate;
        int immediateAsInteger;
        try {
            immediateAsInteger = Integer.parseInt(immediate);
        } catch (NumberFormatException e) {
            immediateAsInteger = Integer.parseInt(immediate.substring(2), 16);
        }
        setOperandA(immediateAsInteger);
    }
    @Override
    public String toString() {
        return String.format("%s mov x%d, %s",
                getPredicateString(), getMappedDestination(), getImmediate());
    }

    @Override
    public String getImmediate() {
        return immediate;
    }
}
