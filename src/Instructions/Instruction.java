package Instructions;

public abstract class Instruction {
    private final int address;
    private int scheduledAddress;
    Instruction(int address) {
        this.address = address;
    }
    public int getAddress() {
        return address;
    }
    public int getScheduledAddress() {
        return scheduledAddress;
    }
    public void setScheduledAddress(int address) {
        this.scheduledAddress = address;
    }
    public abstract int[] getPipelineSlots();
    public abstract String toString();
    public abstract int getLatency();
    public boolean isTrueConsumer() {
        return this instanceof Consumer && !(this instanceof Movi);
    }
}
