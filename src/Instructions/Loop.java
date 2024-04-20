package Instructions;

public class Loop extends Branch {
    public Loop(int address, int target) {
        super(address, target);
    }
    @Override
    public String toString() {
        return String.format(" loop %d", getTarget());
    }
}
