import Instructions.*;
import Microarchitecture.Microarchitecture;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Schedule {
    private final ArrayList<Instruction> program;
    private final ArrayList<Bundle> bundles;
    private final Instruction loopStart;
    private final Instruction loopEnd;
    private boolean loopEndAdded;
    private final int initiationInterval;
    Schedule(ArrayList<Instruction> program, Instruction loopStart, Instruction loopEnd, int initiationInterval) {
        this.program = program;
        this.loopStart = loopStart;
        this.loopEnd = loopEnd;
        this.initiationInterval = initiationInterval;
        bundles = new ArrayList<>();
        loopEndAdded = false;
    }

    protected ArrayList<Bundle> get() {
        return bundles;
    }

    protected void insert(Instruction instruction, InstructionDependency deps, int initiationInterval) {
        // If instruction is the branch instruction, insert into a special spot
        if (instruction instanceof Branch) {
            ((Branch) instruction).setTarget(getLoopStart());
            while (bundles.size() < getLoopStart() + initiationInterval)
                addBundle();
            bundles.get(getLoopStart() + initiationInterval - 1).insertIntoSlot(instruction);
            return;
        }

        int lowerBound = getEarliestSlot(instruction, deps);
        insertFromLowerBound(instruction, lowerBound, false);
    }

    protected void insert(Mov mov) {
        int lowerBound = computeInterloopDependencyMovLowerBoundSlot(mov);
        insertFromLowerBound(mov, lowerBound, true);
    }

    protected void moveLoopToEnd() {
        int loopSlot = Microarchitecture.BR_SLOTS[0];
        for (Bundle b : bundles)
            for (Instruction i : b.get())
                if (i instanceof Branch) {
                    b.get().set(loopSlot, new Nop(b.getAddress(), loopSlot));
                    bundles.get(getLoopEnd()-1).insertIntoSlot(i);
                    return;
                }
        assert false;
    }

    protected boolean containsLoop() {
        return loopStart != null;
    }

    protected int getLoopStart() {
        return loopStart == null ? bundles.size(): loopStart.getScheduledAddress();
    }

    protected int getLoopEnd() {
        return loopEnd == null ? bundles.size() : loopEnd.getScheduledAddress();
    }

    private void insertFromLowerBound(Instruction instruction, int earliest, boolean insertingInterloopMov) {
        Bundle bundle;
        boolean ok;
        int index = earliest;

        if (instruction == loopEnd) {
            loopEndAdded = true;
        }

        do {
            while (index >= bundles.size()) {
                addBundle();
            }

            if (insertingInterloopMov && index == getLoopEnd()) {
                int oldLoopEnd = getLoopEnd();
                insertBubbleBundle(oldLoopEnd);
                loopEnd.setScheduledAddress(oldLoopEnd+1);
            }

            bundle = bundles.get(index++);
            ok = bundle.insertIntoSlot(instruction);
            if (ok) {
                return;
            }
        } while (true);
    }

    private int computeInterloopDependencyMovLowerBoundSlot(Mov mov) {
        AtomicInteger earliest = new AtomicInteger(getLoopEnd() - 1);
        bundles.subList(getLoopStart(), Math.min(getLoopEnd(), bundles.size())).forEach(b ->
                b.get().forEach(i -> {
                    if (!(i instanceof Producer)) {
                        return;
                    }
                    if (((Producer) i).getMappedDestination() == mov.getMappedOperandA()) {
                        if (earliest.get() < i.getScheduledAddress() + i.getLatency()) {
                            earliest.set(i.getScheduledAddress() + i.getLatency());
                        }
                    }
                }));
        return earliest.get();
    }

    private void addBundle() {
        insertBubbleBundle(bundles.size());
    }
    private void insertBubbleBundle(int index) {
        bundles.subList(index, bundles.size()).forEach(b -> b.setAddress(b.getAddress()+1));
        bundles.add(index, new Bundle(index));
    }

    private int getEarliestSlot(Instruction instruction, InstructionDependency dependencies) {
        int lowerBound = (instruction == loopStart || instruction == loopEnd) ? bundles.size() :
                (instruction.getAddress() < getLoopStart()) ? 0 :
                (instruction.getAddress() <= getLoopEnd() || !loopEndAdded) ? getLoopStart() :
                        getLoopEnd();
        int latestDependency = dependencies.getAll().stream()
                .filter(d -> instruction.getAddress() > d.getAddress() &&
                        lowerBound < d.getAddress() + d.getLatency())
                .mapToInt(d -> d.getScheduledAddress() + d.getLatency())
                .max()
                .orElse(0);
        return Math.max(latestDependency, lowerBound);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Bundle b : bundles)
            sb.append(String.format("%-2d %s\n", b.getAddress(), b));
        return sb.toString();
    }
}
