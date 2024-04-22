import Instructions.*;
import Microarchitecture.Microarchitecture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class PipelinedSchedule extends Schedule {
    private int numberOfLoopStages;
    public PipelinedSchedule(Instruction loopStart, Instruction loopEnd,
                             ArrayList<Instruction> program,
                             ArrayList<InstructionDependency> deps) {
        super(loopStart, loopEnd, program, deps);
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
        if (!inLoop(instruction))
            return;

        Instruction o = getNextToReserve(instruction);
        while (o != instruction) {
            assert o instanceof Nop;
            ((Nop) o).markReserved();
//            System.out.printf("\tMarking bundle %d slot %d reserved\n", o.getScheduledAddress(), o.getScheduledSlot());

            o = getNextToReserve(o);
            if (o.getScheduledAddress() == 3 && o.getScheduledSlot() == 3)
                System.exit(1);
        }
    }

    private Instruction getNextToReserve(Instruction c) {
        int next = c.getScheduledAddress() + initiationInterval;

        if (next >= getLoopEndAddress()) {
            int lStart = getLoopStartAddress();
            next = (next - lStart) % (getLoopEndAddress() - lStart) + lStart;
        }

        return bundles.get(next).getBundle().get(c.getScheduledSlot());
    }

    private void reschedule(Instruction end) {
//        System.out.printf("\n\n --- RESCHEDULING --- \n\n");
        System.out.printf("\tRescheduling\n");
        initiationInterval += 1;
        bundles = new ArrayList<>(bundles.subList(0, getLoopStartAddress()));
        loopEndAdded = false;
        program.subList(loopStart.getAddress(), end.getAddress()+1).forEach(this::scheduleInstruction);
//        System.out.printf("\n\n --- FINISHED RESCHEDULING --- \n\n");
        System.out.printf("\tFinished rescheduling\n");
    }

    private void addLoopStage() {
        for (int i = 0; i < initiationInterval; i++)
            pushDownLoopEnd();
        propagateReserved();
    }

    private void propagateReserved() {
        bundles.subList(getLoopStartAddress(), getLoopEndAddress()).forEach(b -> b.getBundle().forEach(i -> {
            if (!(i instanceof Nop)) markReserved(i);
        }));
    }

    private boolean inLoop(Instruction i) {
        return i.getScheduledAddress() >= getLoopStartAddress() && (i.getScheduledAddress() < getLoopEndAddress() || !loopEndAdded);
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
        System.out.printf("Allocated fresh produced registers:\n%s\n", this);
        allocateLoopInvariantSimpleRegisters();
        System.out.printf("Allocated loop invariant registers:\n%s\n", this);
        mapLoopBodyConsumers();
        System.out.printf("Mapped loop body consumers:\n%s\n", this);
        allocateExoLoopRegisters();
        System.out.printf("Allocated remaining registers in BB0 and BB2:\n%s\n", this);
    }

    private int computeNumberOfLoopStages() {
        return (getLoopEndAddress()-getLoopStartAddress())/initiationInterval;
    }

    private void allocateFreshRotatingLoopRegisters() {
        bundles.subList(getLoopStartAddress(), getLoopEndAddress()).forEach(b ->
                b.getBundle().stream().filter(i -> i instanceof Producer).forEach(p -> {
                    int fresh = Microarchitecture.getFreshRotatingRegister(numberOfLoopStages);
                    ((Producer) p).setMappedDestination(fresh);
                }));
    }

    private void allocateLoopInvariantSimpleRegisters() {
        dependencyMatrix.forEach(r -> r.getLoopInvariantDependencies().forEach(p -> {
            int fresh = Microarchitecture.getFreshSimpleRegister();
            p.setMappedDestination(fresh);
        }));
    }

    private void mapLoopBodyConsumers() {
        bundles.subList(getLoopStartAddress(), getLoopEndAddress())
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

    private void allocateExoLoopRegisters() {

    }

    private int calculateLoopStageDiff(Consumer c, Producer p) {
        return (c.getScheduledAddress() - p.getScheduledAddress()) / initiationInterval;
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Bundle b : bundles) {
            if (b.getAddress() >= getLoopStartAddress() && b.getAddress() <= getLoopEndAddress() &&
                    (b.getAddress()-getLoopStartAddress()) % initiationInterval == 0)
                sb.append(String.format("%s\n", "-".repeat(105)));
            sb.append(String.format("%-2d %s\n", b.getAddress(), b));
        }
        return sb.toString();
    }
}
