import Instructions.Branch;
import Instructions.Instruction;

import java.util.ArrayList;
import java.util.OptionalInt;

public abstract class Schedule {
    private final ArrayList<Instruction> program;
    private final ArrayList<Bundle> bundles;
    private final int initialLoopStart;
    private int loopStart = -1, loopEnd = -1;
    private final int initiationInterval;
    Schedule(ArrayList<Instruction> program, int initiationInterval, int initialLoopStart) {
        this.program = program;
        this.initiationInterval = initiationInterval;
        this.initialLoopStart = initialLoopStart;
        bundles = new ArrayList<>();
    }

    protected ArrayList<Bundle> get() {
        return bundles;
    }
    protected int getLoopStart() {
        return loopStart;
    }
    protected int getLoopEnd() {
        return loopEnd;
    }

    protected void insert(Instruction instruction, InstructionDependency deps, int initiationInterval) {
        int i;
        boolean ok;
        Bundle bundle;

        // Set loop start to the current size
        if (instruction.getAddress() == initialLoopStart)
            defineLoopStart();

        // If instruction is the branch instruction, insert into a special spot
        if (instruction instanceof Branch) {
            defineLoopEnd();
            ((Branch) instruction).setTarget(loopStart);
            bundles.get(loopStart + initiationInterval).insertIntoSlot(instruction);
            return;
        }

        i = getEarliestSlot(instruction, deps);
        do {
            while (i >= bundles.size())
                addBundle();
            bundle = bundles.get(i++);
            ok = bundle.insertIntoSlot(instruction);
            if (ok) {
                return;
            }
        } while (true);
    }
    private void addBundle() {
        insertBubbleBundle(bundles.size());
    }
    private void insertBubbleBundle(int index) {
        // TODO: update loopStart and loopEnd
        bundles.add(index, new Bundle(index));
    }

    private void addLoopStage(int initiationInterval) {
        // TODO: update loopStart and loopEnd
    }

    private void defineLoopStart() {
        loopStart = bundles.size();
    }
    private boolean loopStartDefined() {
        return initialLoopStart != -1;
    }
    private void defineLoopEnd() {
        loopEnd = bundles.size();
    }
    private boolean loopEndDefined() {
        return loopEnd != -1;
    }
    private ArrayList<Bundle> getBasicBlockZero() {
        return (ArrayList<Bundle>) bundles.subList(0, initialLoopStart);
    }
    private ArrayList<Bundle> getBasicBlockOne() {
        return (ArrayList<Bundle>) bundles.subList(initialLoopStart, loopEndDefined() ? loopEnd : bundles.size());
    }
    private ArrayList<Bundle> getBasicBlockTwo() {
        return (ArrayList<Bundle>) bundles.subList(loopEnd, bundles.size());
    }
    private int getEarliestSlot(Instruction instruction, InstructionDependency dependencies) {
        int lb = (!loopStartDefined() || instruction.getAddress() < initialLoopStart) ? 0 :
                (!loopEndDefined() || instruction.getAddress() > initialLoopStart) ? loopStart : loopEnd;
        int dependencyDelay = dependencies.getAll().stream()
                .filter(d -> instruction.getAddress() > d.getAddress() &&
                        lb < d.getAddress() + d.getLatency())
                .mapToInt(d -> d.getScheduledAddress() + d.getLatency())
                .max()
                .orElse(0);
        return Math.max(dependencyDelay, lb);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Bundle b : bundles)
            sb.append(String.format("%-2d %s\n", b.getAddress(), b.toString()));
        return sb.toString();
    }
}
