package Instructions;

public abstract class Instruction {
    private final int address;
    private int scheduledAddress;
    Instruction(int address) {
        this.address = address;
        this.scheduledAddress = address;
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
    protected int parseImmediate(String imm) {
        int asInt;
        try {
            asInt = Integer.parseInt(imm);
        } catch (NumberFormatException e) {
            asInt = Integer.parseInt(imm.substring(2), 16);
        }
        return asInt;
    }
}
