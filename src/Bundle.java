import Instructions.Instruction;
import Instructions.Nop;
import Microarchitecture.Microarchitecture;

import java.util.ArrayList;

public class Bundle {
    private final ArrayList<Instruction> bundle;
    private final int address;
    Bundle(int pc) {
        this.address = pc;
        bundle = new ArrayList<>(Microarchitecture.PIPELINE_WIDTH);
        for (int i = 0; i < Microarchitecture.PIPELINE_WIDTH; i++)
            bundle.add(new Nop(pc, i));
    }

    ArrayList<Instruction> get() {
        return bundle;
    }

    int getAddress() {
        return address;
    }

    boolean insertIntoSlot(Instruction instruction) {
        for (Integer i : instruction.getPipelineSlots()) {
            if (bundle.get(i) instanceof Nop && !((Nop)bundle.get(i)).isReserved()) {
                instruction.setScheduledAddress(address);
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
            sb.append(String.format(" | %-20s", i.toString()));
        return sb.toString();
    }
}
