import Instructions.*;
import Microarchitecture.Microarchitecture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public abstract class Schedule {
    protected ArrayList<Bundle> bundles;
    protected final Instruction loopStart;
    protected final Instruction loopEnd;
    protected final ArrayList<Instruction> program;
    protected final ArrayList<InstructionDependency> dependencyMatrix;
    protected int initiationInterval;
    protected boolean loopEndAdded;
    protected Branch branchInstruction;

    public abstract void allocateRegisters();
    protected abstract boolean insertionLoop(Instruction instruction, int index);
    public abstract void prepareLoop();

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

    protected int getLoopEndScheduledAddress() {
        return loopEnd == null ? bundles.size() : loopEnd.getScheduledAddress();
    }

    protected boolean containsLoop() {
        return loopStart != null;
    }

    protected void addBundle() {
        insertBubbleBundle(bundles.size());
    }

    protected void insertBubbleBundle(int index) {
        if (index <= getLoopStartAddress() && branchInstruction != null) {
            branchInstruction.setTarget(branchInstruction.getTarget() + 1);
        }

        bundles.subList(index, bundles.size()).forEach(b -> {
            int newAddress = b.getAddress() + 1;
            b.getBundle().forEach(i -> i.setScheduledAddress(newAddress));
            b.setAddress(newAddress);
        });
        bundles.add(index, new Bundle(index));
    }

    protected void insertBranch(Branch branch) {
        Branch loop = (this instanceof SequentialSchedule) ?
                new Loop(branch.getAddress(), getLoopStartAddress()) :
                new LoopPip(branch.getAddress(), getLoopStartAddress());
        while (bundles.size() < getLoopStartAddress() + initiationInterval) {
            addBundle();
        }

        branchInstruction = loop;
        program.set(branch.getAddress(), loop);
        int address = getLoopStartAddress() + initiationInterval - 1;
        loop.setScheduledAddress(address);
        bundles.get(address).insertIntoSlot(loop);
    }

    protected void pushDownLoopEnd() {
        int oldLoopEnd = loopEndAdded ? getLoopEndScheduledAddress() : bundles.size();
        insertBubbleBundle(oldLoopEnd);
        if (loopEndAdded) loopEnd.setScheduledAddress(oldLoopEnd+1); // TODO: remove?
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
                    bundles.get(getLoopEndScheduledAddress()-1).insertIntoSlot(i);
                    return;
                }
        assert false;
    }

    protected int getEarliestSlot(Instruction i, InstructionDependency deps) {
        int lowerBound =
                (!containsLoop()) ? 0 :
                        (i == loopStart || i == loopEnd) ? bundles.size() :
                                (i.getAddress() < getLoopStartAddress()) ? 0 :
                                        (i.getAddress() <= getLoopEndScheduledAddress() || !loopEndAdded) ? getLoopStartAddress() :
                                                getLoopEndScheduledAddress();
        int latestDependency = deps.getAll().stream()
                .filter(d -> i.getAddress() > d.getAddress() &&
                        lowerBound < d.getAddress() + d.getLatency())
                .mapToInt(d -> d.getScheduledAddress() + d.getLatency())
                .max()
                .orElse(0);
        return Math.max(latestDependency, lowerBound);
    }

    protected void allocateEarlierReaders() {
        bundles.forEach(b ->
                b.getBundle().stream()
                        .filter(Instruction::isTrueConsumer)
                        .forEach(i -> {
                            if (!((Consumer)i).isOperandARemapped()) {
                                ((Consumer) i).setOperandA(Microarchitecture.getFreshSimpleRegister());
                            }
                            if (!(i instanceof DoubleConsumer))
                                return;

                            if (!((DoubleConsumer)i).isOperandBRemapped()) {
                                ((DoubleConsumer) i).setOperandB(Microarchitecture.getFreshSimpleRegister());
                            }
                        })
        );
    }

    protected ArrayList<Producer> getDistinctInterloopDependencies() {
        ArrayList<Producer> interloopDeps = new ArrayList<>();
        dependencyMatrix
                .subList(getInitialLoopStartAddress(), getInitialLoopEndAddress())
                .forEach(d -> d.getInterloopDependencies().stream()
                        .filter(p -> p.getScheduledAddress() < getLoopStartAddress())
                        .forEach(interloopDeps::add));
        return interloopDeps.stream().collect(Collectors.collectingAndThen(
                Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Instruction::toString))),
                ArrayList::new));
    }

    protected ArrayList<Producer> getDistinctDependencies() {
        ArrayList<Producer> producers = new ArrayList<>();
        dependencyMatrix.forEach(d -> producers.addAll(d.getInterloopDependencies()));
        return producers.stream().collect(Collectors.collectingAndThen(
                Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Instruction::toString))),
                ArrayList::new));
    }

    protected void mapConsumed(ArrayList<Bundle> bundles) {
        bundles.forEach(b ->
                b.getBundle().stream()
                        .filter(i -> i.isTrueConsumer() && !dependencyMatrix.get(i.getAddress()).getAll().isEmpty())
                        .forEach(c -> {

                            if (dependencyMatrix.get(c.getAddress()).getAll().stream()
                                    .anyMatch(d -> d.getDestination() == ((Consumer) c).getOperandA())) {
                                Producer producer = getMostRecentProducer(c.getAddress(), ((Consumer) c).getOperandA());
                                ((Consumer) c).setOperandA(producer.getMappedDestination());
                            }

                            if (!(c instanceof DoubleConsumer))
                                return;

                            if (dependencyMatrix.get(c.getAddress()).getAll().stream()
                                    .anyMatch(d -> d.getDestination() == ((DoubleConsumer) c).getOperandB())) {
                                Producer producer = getMostRecentProducer(c.getAddress(), ((DoubleConsumer) c).getOperandB());
                                ((DoubleConsumer) c).setOperandB(producer.getMappedDestination());
                            }
                        }));
    }

    protected Producer getMostRecentProducer(int consumerAddress, int register) {
        ArrayList<Producer> producers = dependencyMatrix.get(consumerAddress).getAll().stream()
                .filter(p -> p.getDestination() == register)
                .sorted(Comparator.comparingInt(p -> {
                    int diff = consumerAddress - p.getAddress();
                    return diff <= 0 ? Integer.MAX_VALUE : diff;
                }))
                .collect(Collectors.toCollection(ArrayList::new));
        return producers.get(0);
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
