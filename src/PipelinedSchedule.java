import Instructions.*;
import Microarchitecture.Microarchitecture;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class PipelinedSchedule extends Schedule {
    private int numberOfLoopStages;
    private boolean collapsed;
    public PipelinedSchedule(Instruction loopStart, Instruction loopEnd,
                             ArrayList<Instruction> program,
                             ArrayList<InstructionDependency> deps) {
        super(loopStart, loopEnd, program, deps);
        collapsed = false;
    }

    @Override
    public void scheduleInstruction(Instruction instruction) {
        boolean validSchedule;
        super.scheduleInstruction(instruction);
        if (!(instruction instanceof Consumer))
            return;
        validSchedule = verifyInterloopDependencyInvariant((Consumer) instruction);
        if (!validSchedule) {
            reschedule(instruction);
        }
//        System.out.printf("Scheduled %d%s:\n\n%s\n", instruction.getAddress(), instruction, this);
    }

    @Override
    protected boolean insertionLoop(Instruction instruction, int index) {
        Bundle bundle;
        boolean ok;

//        System.out.printf("Attempting to insert %d%s at %d\n", instruction.getAddress(), instruction, index);

        if (inLoop(instruction) && index >= bundles.size()) {
            addLoopStage();
        }

        while (index >= bundles.size()) {
            addBundle();
        }

        bundle = bundles.get(index);
        ok = bundle.insertIntoSlot(instruction);

        if (!ok)
            return false;

        markReserved(instruction);
        return true;
    }

    private void markReserved(Instruction instruction) {
        if (collapsed)
            return;

        if (!inLoop(instruction))
            return;

        Instruction o = getNextToReserve(instruction);
        while (o != instruction) {
            assert o instanceof Nop;
            ((Nop) o).markReserved();
//            System.out.printf("\tMarking bundle %d slot %d reserved\n", o.getScheduledAddress(), o.getScheduledSlot());
            o = getNextToReserve(o);
        }
    }

    private Instruction getNextToReserve(Instruction c) {
        int next = c.getScheduledAddress() + initiationInterval;

        if (next >= (loopEndAdded ? getLoopEndScheduledAddress() : bundles.size())) {
            int lStart = getLoopStartAddress();
            next = (next - lStart) % ((loopEndAdded ? getLoopEndScheduledAddress() : bundles.size()) - lStart) + lStart;
        }

        return bundles.get(next).getBundle().get(c.getScheduledSlot());
    }

    private void addLoopStage() {
        for (int i = 0; i < initiationInterval; i++)
            pushDownLoopEnd();
        propagateReserved();
    }

    private void propagateReserved() {
        bundles.subList(getLoopStartAddress(), loopEndAdded ? getLoopEndScheduledAddress() : bundles.size())
                .forEach(b -> b.getBundle().forEach(i -> {
                    if (!(i instanceof Nop)) markReserved(i);
                }));
    }

    private void reschedule(Instruction end) {
        initiationInterval += 1;
        bundles = new ArrayList<>(bundles.subList(0, getLoopStartAddress()));
        loopEndAdded = false;
        program.subList(loopStart.getAddress(), end.getAddress()+1).forEach(this::scheduleInstruction);
    }

    private boolean inLoop(Instruction i) {
        return i.getScheduledAddress() >= getLoopStartAddress() && (i.getScheduledAddress() < getLoopEndScheduledAddress() || !loopEndAdded);
    }

    private boolean verifyInterloopDependencyInvariant(Consumer c) {
        for (Producer p : dependencyMatrix.get(c.getAddress()).getInterloopDependencies()) {
            // TODO: remove?
            if (p.getScheduledAddress() > c.getScheduledAddress())
                continue;
            if (!interloopDependencyInvariant(p, c)) {
//                System.out.printf("%d%s made %d%s be scheduled in wrong bundle\n", p.getAddress(), p, c.getAddress(), c);
                return false;
            }
        }

        return true;
    }

    private boolean interloopDependencyInvariant(Instruction p, Instruction c) {
        return p.getScheduledAddress() + p.getLatency() <= c.getScheduledAddress() + initiationInterval;
    }

    /* Register allocation */

    @Override
    public void allocateRegisters() {
        numberOfLoopStages = computeNumberOfLoopStages();
        allocateFreshRotatingLoopRegisters();
//        System.out.printf("Allocated fresh produced registers:\n%s\n", this);
        allocateLoopInvariantSimpleRegisters();
//        System.out.printf("Allocated loop invariant registers:\n%s\n", this);
        mapLoopBodyConsumers();
//        System.out.printf("Mapped loop body consumers:\n%s\n", this);
        mapRemainingRegisters();
//        System.out.printf("Allocated remaining registers in BB0 and BB2:\n%s\n", this);
    }

    private int computeNumberOfLoopStages() {
        if (initiationInterval == 0)
            return 0;
        return (getLoopEndScheduledAddress()-getLoopStartAddress())/initiationInterval;
    }

    private void allocateFreshRotatingLoopRegisters() {
        bundles.subList(getLoopStartAddress(), getLoopEndScheduledAddress()).forEach(b ->
                b.getBundle().stream().filter(i -> i instanceof Producer).forEach(p -> {
                    int fresh = Microarchitecture.getFreshRotatingRegister(numberOfLoopStages);
                    ((Producer) p).setMappedDestination(fresh);
                }));
    }

    private void allocateLoopInvariantSimpleRegisters() {
        ArrayList<Producer> deps = new ArrayList<>();
        dependencyMatrix.forEach(d -> d.getLoopInvariantDependencies().stream().filter(i -> !deps.contains(i)).forEach(deps::add));
        deps.forEach(p -> {
            int fresh = Microarchitecture.getFreshSimpleRegister();
            p.setMappedDestination(fresh);
        });
    }

    private void mapLoopBodyConsumers() {
        bundles.subList(getLoopStartAddress(), getLoopEndScheduledAddress())
                .forEach(b -> b.getBundle().stream().filter(Instruction::isTrueConsumer)
                        .forEach(i -> {
                                Consumer c = (Consumer)i;
                                InstructionDependency deps = dependencyMatrix.get(c.getAddress());

                                // Loop invariants
                                deps.getLoopInvariantDependencies().forEach(p ->
                                        matchAndRemapConsumer(c, p.getDestination(), p.getMappedDestination()));

                                // Local dependencies
                                deps.getLocalDependencies().forEach(p -> {
                                        int diff = calculateLoopStageDiff(c, p);
                                        matchAndRemapConsumer(c, p.getDestination(), p.getMappedDestination()+diff);
                                });

                                // Interloop dependencies
                                deps.getInterloopDependencies().forEach(p -> {
                                        int diff = calculateLoopStageDiff(c, p);
                                        matchAndRemapConsumer(c, p.getDestination(), p.getMappedDestination()+diff+1);
                                });
                        })

                );
    }

    private void mapRemainingRegisters() {
        resolveBasicBlockZeroInterloopProducers();
//        System.out.printf("Resolved basic block zero interloop producers:\n%s\n", this);
        resolveBasicBlockLocalDependencies(new ArrayList<>(bundles.subList(0, getLoopStartAddress())));
        resolveBasicBlockLocalDependencies(new ArrayList<>(bundles.subList(getLoopEndScheduledAddress(), bundles.size())));
//        System.out.printf("Resolved basic block local dependencies:\n%s\n", this);
        resolvePostLoopDependencies();
//        System.out.printf("Resolved post loop dependencies:\n%s\n", this);
        resolveBasicBlockInvariants(new ArrayList<>(bundles.subList(0, getLoopStartAddress())));
        resolveBasicBlockInvariants(new ArrayList<>(bundles.subList(getLoopEndScheduledAddress(), bundles.size())));
//        System.out.printf("Resolved basic block invariants:\n%s\n", this);
//        System.out.printf("Allocated independent instructions:\n%s\n", this);
        allocateIndependentRegisters();
        allocatePostLoopIndependentRegisters();
    }

    private void matchAndRemapConsumer(Consumer c, int old, int mapped) {
        if (c.getOperandA() == old) {
            c.setOperandA(mapped);
        }

        if (!(c instanceof DoubleConsumer)) {
            return;
        }

        if (((DoubleConsumer)c).getOperandB() == old) {
            ((DoubleConsumer)c).setOperandB(mapped);
        }
    }

    private void resolveBasicBlockZeroInterloopProducers() {
        // BB0 registers that are interloop dependencies
        ArrayList<Producer> interloopDeps = getDistinctInterloopDependencies();
//        System.out.printf("Distinct interloop dependencies: %s\n", interloopDeps.toString());

        // For each instruction in BB0...
        bundles.subList(0, getLoopStartAddress()).forEach(b ->
                b.getBundle().stream().filter(i -> i instanceof Producer).forEach(p -> {
//                    System.out.printf("Looking if %d%s is an interloop dependency\n", p.getAddress(), p);
                    interloopDeps.stream().filter(i -> i == p).forEach(i -> {
//                        System.out.printf("\tFor %d%s, found our register to be an interloop dependent register\n",
//                                p.getAddress(), p, i.getAddress(), i);

                        // ... that is also produced in the loop
                        Optional<Producer> opt = getLoopProducers().stream()
                                .filter(l -> l.getDestination() == ((Producer)p).getDestination())
                                .findFirst();

                        if (opt.isEmpty())
                            return;

                        int loopStage = getLoopStageOfInstruction(opt.get());
                        int mappedAddress = (opt.get().getMappedDestination() - loopStage) + 1;
//                        System.out.printf("\tFound instruction: %d%s\n", opt.get().getAddress(), opt.get());
//                        System.out.printf("\tSetting mapped register to x%d(%d, %d)\n", opt.get().getMappedDestination(), 1, -loopStage);
                        ((Producer)p).setMappedDestination(mappedAddress);
                    });
        }));
    }

    private int getLoopStageOfInstruction(Instruction instruction) {
        return (instruction.getScheduledAddress() - getLoopStartAddress()) / initiationInterval;
    }

    private int calculateLoopStageDiff(Instruction i2, Instruction i1) {
        return getLoopStageOfInstruction(i2) - getLoopStageOfInstruction(i1);
    }

    private ArrayList<Producer> getLoopProducers() {
        ArrayList<Producer> producers = new ArrayList<>();
        bundles.subList(getLoopStartAddress(), getLoopEndScheduledAddress())
                .forEach(b -> b.getBundle().stream()
                        .filter(i -> i instanceof Producer)
                        .map(p -> (Producer)p).forEach(producers::add));
        return producers;
    }

    private void resolveBasicBlockLocalDependencies(ArrayList<Bundle> bb) {
        // Allocate fresh registers to producers and map the consumed ones
        bb.forEach(b -> b.getBundle().stream().filter(i -> i instanceof Consumer).forEach(c ->
                dependencyMatrix.get(c.getAddress()).getLocalDependencies().forEach(p -> {
                            Consumer consumer = (Consumer) c;
                            int fresh = Microarchitecture.getFreshSimpleRegister();
                            p.setMappedDestination(fresh);

                            if (consumer.getOperandA() == p.getDestination())
                                consumer.setOperandA(fresh);

                            if (!(consumer instanceof DoubleConsumer))
                                return;

                            if (((DoubleConsumer)c).getOperandB() == p.getDestination())
                                ((DoubleConsumer)c).setOperandB(fresh);

//                            System.out.printf("Allocated fresh register to %d%s\n", p.getAddress(), p);

                        })));
    }

    private void resolvePostLoopDependencies() {
        bundles.subList(getLoopEndScheduledAddress(), bundles.size()).forEach(b -> b.getBundle().stream()
                .filter(i -> i instanceof Consumer).forEach(c -> {
                    if (dependencyMatrix.get(c.getAddress()).getPostLoopDependencies().stream().anyMatch(d -> d.getDestination() == ((Consumer) c).getOperandA())) {
                        Producer producer = getMostRecentProducer(c.getAddress(), ((Consumer) c).getOperandA());
                        int dest = producer.getMappedDestination() + calculateLoopStageDiff(bundles.get(getLoopEndScheduledAddress()-1).getBundle().get(0), producer);
//                        System.out.printf("Got producer of %d%s A from %d%s with loop stage diff %d. Setting operand A to x%d\n", c.getAddress(), c, producer.getAddress(), producer, calculateLoopStageDiff(bundles.get(getLoopEndAddress()-1).getBundle().get(0), producer), dest);
                        ((Consumer) c).setOperandA(dest);
                    }

                    if (!(c instanceof DoubleConsumer))
                        return;

                    if (dependencyMatrix.get(c.getAddress()).getPostLoopDependencies().stream().anyMatch(d -> d.getDestination() == ((DoubleConsumer) c).getOperandB())) {
                        Producer producer = getMostRecentProducer(c.getAddress(), ((DoubleConsumer) c).getOperandB());
                        int dest = producer.getMappedDestination() + calculateLoopStageDiff(bundles.get(getLoopEndScheduledAddress()-1).getBundle().get(0), producer);
//                        System.out.printf("Got producer of %d%s B from %d%s with loop stage diff %d. Setting operand B to x%d\n", c.getAddress(), c, producer.getAddress(), producer, calculateLoopStageDiff(bundles.get(getLoopEndAddress()-1).getBundle().get(0), producer), dest);
                        ((DoubleConsumer) c).setOperandB(dest);
                    }
                }));
    }

    private void resolveBasicBlockInvariants(ArrayList<Bundle> bb) {
        bb.forEach(b -> b.getBundle().stream()
                .filter(i -> i instanceof Consumer).forEach(c -> {

                    if (dependencyMatrix.get(c.getAddress()).getLoopInvariantDependencies().stream().anyMatch(d -> d.getDestination() == ((Consumer) c).getOperandA())) {
                        Producer producer = getMostRecentProducer(c.getAddress(), ((Consumer) c).getOperandA());
                        ((Consumer) c).setOperandA(producer.getMappedDestination());
                    }

                    if (!(c instanceof DoubleConsumer))
                        return;

                    if (dependencyMatrix.get(c.getAddress()).getLoopInvariantDependencies().stream().anyMatch(d -> d.getDestination() == ((DoubleConsumer) c).getOperandB())) {
                        Producer producer = getMostRecentProducer(c.getAddress(), ((DoubleConsumer) c).getOperandB());
                        ((DoubleConsumer) c).setOperandB(producer.getMappedDestination());
                    }
                }
            ));
    }

    private void allocateIndependentProducers() {
        ArrayList<Producer> distinct = getDistinctDependencies();
        bundles.forEach(b -> b.getBundle().stream().filter(i -> (i instanceof Producer)).forEach(p -> {
            if (!distinct.contains(p) && !((Producer) p).destinationIsRemapped()) {
                int fresh = Microarchitecture.getFreshSimpleRegister();
                ((Producer) p).setMappedDestination(fresh);
            }
        }));
    }

    private void allocateIndependentRegisters() {
        HashSet<Integer> indep = getIndependentRegisters();
//        System.out.printf("Independent registers: %s\n", indep);
        bundles.forEach(b -> b.getBundle().stream().filter(i -> (!(i instanceof Nop)))
                .forEach(i -> {
//                        System.out.printf("Mapped instruction %d%s to", i.getAddress(), i);
                        if (!inLoop(i) && i instanceof Producer && indep.contains(((Producer) i).getDestination()))
                            ((Producer) i).setMappedDestination(Microarchitecture.getFreshSimpleRegister());
                        if (i.isTrueConsumer() && indep.contains(((Consumer) i).getOperandA()))
                            ((Consumer) i).setOperandA(Microarchitecture.getFreshSimpleRegister());
                        if (i instanceof DoubleConsumer && indep.contains(((DoubleConsumer) i).getOperandB()))
                            ((DoubleConsumer) i).setOperandB(Microarchitecture.getFreshSimpleRegister());
//                        System.out.printf(" %d%s\n", i.getAddress(), i);
        }));
    }

    private HashSet<Integer> getIndependentRegisters() {
        HashMap<Integer, Integer> regs = new HashMap<>();
        BiConsumer<HashMap<Integer, Integer>, Integer> updateMap = (map, key) -> {
            map.merge(key, 1, Integer::sum);
        };

        program.forEach(i -> {
            if (i instanceof Producer)
                updateMap.accept(regs, ((Producer) i).getDestination());
            if (i instanceof Consumer)
                updateMap.accept(regs, ((Consumer) i).getOperandA());
            if (i instanceof DoubleConsumer)
                updateMap.accept(regs, ((DoubleConsumer) i).getOperandB());
        });

        return regs.entrySet().stream()
                .filter(entry -> entry.getValue() == 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void allocatePostLoopIndependentRegisters() {
        bundles.subList(getLoopEndScheduledAddress(), bundles.size()).forEach(b -> b.getBundle().stream()
                .filter(i -> i instanceof Producer)
                .forEach(p ->
                    ((Producer)p).setMappedDestination(Microarchitecture.getFreshSimpleRegister())));
    }

    @Override
    public void prepareLoop() {
        addPredicates();
//        System.out.printf("Added predicates:\n%s\n", this);
        collapseLoop();
//        System.out.printf("Collapsed schedule:\n%s\n", this);
        insertPrepareInstructions();
//        System.out.printf("Inserted prepare instructions:\n%s\n", this);
    }

    private void insertPrepareInstructions() {
        MovLoop movLoop = new MovLoop(getLoopStartAddress()-1, "EC", numberOfLoopStages-1);
        forceScheduleInstruction(movLoop);
        MovP movP = new MovP(getLoopStartAddress()-1, 32, "true");
        forceScheduleInstruction(movP);
    }

    private void forceScheduleInstruction(Instruction instruction) {
        Bundle b = bundles.get(instruction.getAddress());
        for (int i = 0; i < instruction.getPipelineSlots().length; i++) {
            if (b.getBundle().get(i) instanceof Nop) {
                tryInsertFrom(instruction, b.getAddress(), this::insertionLoop);
                return;
            }
        }
        insertBubbleBundle(instruction.getAddress()+1);
        tryInsertFrom(instruction, instruction.getAddress()+1, this::insertionLoop);
    }

    public void collapseLoop() {
        collapsed = true;
//        System.out.printf("Indices to collapse: %d -> %d\n", getLoopStartAddress() + initiationInterval, getLoopEndScheduledAddress());
        bundles.subList(getLoopStartAddress() + initiationInterval, getLoopEndScheduledAddress())
                .forEach(b -> b.getBundle().stream()
                        .filter(i -> !(i instanceof Nop))
                        .forEach(i -> {
                            int slot = i.getScheduledSlot();
                            int dest = (i.getScheduledAddress() - getLoopStartAddress()) % initiationInterval + getLoopStartAddress();
                            assert bundles.get(dest).getBundle().get(slot) instanceof Nop;
                            i.setScheduledAddress(dest);
                            bundles.get(dest).getBundle().set(slot, i);
                        }));
        bundles.subList(getLoopStartAddress() + initiationInterval, getLoopEndScheduledAddress()).clear();
        bundles.subList(getLoopStartAddress() + initiationInterval, bundles.size())
                .forEach(b -> {
                    AtomicInteger index = new AtomicInteger(getLoopStartAddress() + initiationInterval);
                    b.getBundle().forEach(i -> i.setScheduledAddress(index.get()));
                    b.setAddress(index.getAndIncrement());
                });
    }

    private void addPredicates() {
        for (int i = getLoopStartAddress(); i < getLoopEndScheduledAddress(); i += initiationInterval) {
            int pred = Microarchitecture.getFreshRotatingPredicate();
            bundles.subList(i, i + initiationInterval)
                    .forEach(b -> b.getBundle().stream()
                            .filter(instr -> (instr instanceof Predicateable))
                            .forEach(p -> ((Predicateable) p).setPredicate(pred)
                            ));
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Bundle b : bundles) {
            if (b.getAddress() >= getLoopStartAddress() && b.getAddress() <= getLoopEndScheduledAddress() &&
                    (b.getAddress()-getLoopStartAddress()) % initiationInterval == 0)
                sb.append(String.format("%s\n", "-".repeat(125)));
            sb.append(String.format("%-2d %s\n", b.getAddress(), b));
        }
        return sb.toString();
    }
}
