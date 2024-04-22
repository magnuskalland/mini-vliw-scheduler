import Instructions.*;
import Microarchitecture.Microarchitecture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiFunction;

public abstract class Schedule {
    protected ArrayList<Bundle> bundles;
    protected final Instruction loopStart;
    protected final Instruction loopEnd;
    protected final ArrayList<Instruction> program;
    protected final ArrayList<InstructionDependency> dependencyMatrix;
    protected int initiationInterval;
    protected boolean loopEndAdded;

    public abstract void allocateRegisters();
    protected abstract boolean insertionLoop(Instruction instruction, int index);

    public void scheduleInstruction(Instruction instruction) {
        // If instruction is the branch instruction, insert into a special spot
        if (instruction instanceof Branch) {
            insertBranch((Branch)instruction);
            return;
        }

        InstructionDependency deps = dependencyMatrix.get(instruction.getAddress());
        int lowerBound = getEarliestSlot(instruction, deps);
        tryInsertFrom(instruction, lowerBound, this::insertionLoop);
    }

    protected void tryInsertFrom(Instruction instruction, int lb, BiFunction<Instruction, Integer, Boolean> loopFunction) {
        if (instruction == loopEnd) {
            loopEndAdded = true;
        }

        boolean ok;
        int index = lb;
        do {
            ok = loopFunction.apply(instruction, index);
            index += 1;
        }
        while(!ok);
    }

    Schedule(Instruction loopStart, Instruction loopEnd,
             ArrayList<Instruction> program,
             ArrayList<InstructionDependency> dependencyMatrix) {
        this.loopStart = loopStart;
        this.loopEnd = loopEnd;
        this.program = program;
        this.dependencyMatrix = dependencyMatrix;
        this.initiationInterval = computeInitiationIntervalLowerBound();
        bundles = new ArrayList<>();
        loopEndAdded = false;
    }

    public int getInitiationInterval() {
        return initiationInterval;
    }

    protected ArrayList<Bundle> get() {
        return bundles;
    }

    protected int getLoopStartAddress() {
        return loopStart == null ? bundles.size(): loopStart.getScheduledAddress();
    }

    protected int getLoopEndAddress() {
        return loopEnd == null ? bundles.size() : loopEnd.getScheduledAddress();
    }

    protected boolean containsLoop() {
        return loopStart != null;
    }

    protected void addBundle() {
        insertBubbleBundle(bundles.size());
    }

    protected void insertBubbleBundle(int index) {
        bundles.subList(index, bundles.size()).forEach(b -> b.setAddress(b.getAddress()+1));
        bundles.add(index, new Bundle(index));
    }

    protected void insertBranch(Branch branch) {
        Branch loop = (this instanceof SequentialSchedule) ?
                new Loop(branch.getAddress(), getLoopStartAddress()) :
                new LoopPip(branch.getAddress(), getLoopStartAddress());
        while (bundles.size() < getLoopStartAddress() + initiationInterval) {
            addBundle();
        }
        bundles.get(getLoopStartAddress() + initiationInterval - 1).insertIntoSlot(loop);
    }

    protected void pushDownLoopEnd() {
        int oldLoopEnd = loopEndAdded ? getLoopEndAddress() : bundles.size();
        insertBubbleBundle(oldLoopEnd);
        loopEnd.setScheduledAddress(oldLoopEnd+1);
    }

    private int computeInitiationIntervalLowerBound() {
        int[] counts = new int[Microarchitecture.PIPELINE_WIDTH];
        program.subList(getInitialLoopStartAddress(), getInitialLoopEndAddress())
                .forEach(i -> counts[i.getPipelineSlots()[0]]++);
        counts[0] = (int) Math.ceil((double) counts[0] / Microarchitecture.ALU_UNITS);
        return Arrays.stream(counts).max().getAsInt();
    }

    protected void moveLoopToEnd() {
        int loopSlot = Microarchitecture.BR_SLOTS[0];
        for (Bundle b : bundles)
            for (Instruction i : b.getBundle())
                if (i instanceof Branch) {
                    b.getBundle().set(loopSlot, new Nop(b.getAddress()));
                    bundles.get(getLoopEndAddress()-1).insertIntoSlot(i);
                    return;
                }
        assert false;
    }

    protected int getEarliestSlot(Instruction i, InstructionDependency deps) {
        int lowerBound =
                (!containsLoop()) ? 0 :
                        (i == loopStart || i == loopEnd) ? bundles.size() :
                                (i.getAddress() < getLoopStartAddress()) ? 0 :
                                        (i.getAddress() <= getLoopEndAddress() || !loopEndAdded) ? getLoopStartAddress() :
                                                getLoopEndAddress();
        int latestDependency = deps.getAll().stream()
                .filter(d -> i.getAddress() > d.getAddress() &&
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

    private int getInitialLoopStartAddress() {
        return loopStart == null ? program.size() : loopStart.getAddress();
    }

    private int getInitialLoopEndAddress() {
        return loopEnd == null ? program.size() : loopEnd.getAddress();
    }
}
