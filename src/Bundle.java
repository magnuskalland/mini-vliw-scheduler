import Instructions.Instruction;
import Instructions.Nop;
import Microarchitecture.Microarchitecture;

import java.util.ArrayList;

public class Bundle {
    private final ArrayList<Instruction> bundle;
    private int address;
    public Bundle(int pc) {
        this.address = pc;
        bundle = new ArrayList<>(Microarchitecture.PIPELINE_WIDTH);
        for (int i = 0; i < Microarchitecture.PIPELINE_WIDTH; i++) {
            Nop nop = new Nop(pc);
            nop.setScheduledSlot(i);
            bundle.add(nop);
        }
    }

    protected ArrayList<Instruction> getBundle() {
        return bundle;
    }
    protected int getAddress() {
        return address;
    }
    protected void setAddress(int address) {
        this.address = address;
    }

    protected boolean insertIntoSlot(Instruction instruction) {
        for (Integer i : instruction.getPipelineSlots()) {
            if (bundle.get(i) instanceof Nop && !((Nop)bundle.get(i)).isReserved()) {
                instruction.setScheduledAddress(address);
                instruction.setScheduledSlot(i);
                bundle.set(i, instruction);
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Instruction i : bundle)
            sb.append(String.format(" | %-25s", i.toString()));
        return sb.toString();
    }
}
