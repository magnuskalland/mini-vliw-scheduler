package Instructions;

public class Movi extends Arithmetic implements Immediate {
    private final String immediate;

    public Movi(int address, int destination, String immediate) {
        super(address, destination, -1);
        this.immediate = immediate;
        setOperandA(parseImmediate(immediate));
    }
    @Override
    public String toString() {
        return String.format("%s mov x%d, %s",
                getPredicateString(), getMappedDestination(), getMappedOperandA());
    }

    @Override
    public String getImmediate() {
        return immediate;
    }
}
