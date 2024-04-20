package Instructions;

public class LoopPip extends Branch {

    public LoopPip(int address, int target) {
        super(address, target);
    }

    @Override
    public String toString() {
        return String.format(" loop.pip %d", getTarget());
    }
}
