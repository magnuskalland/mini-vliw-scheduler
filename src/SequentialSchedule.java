import Instructions.*;
import Microarchitecture.Microarchitecture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SequentialSchedule extends Schedule {
    public SequentialSchedule(Instruction loopStart, Instruction loopEnd,
                              ArrayList<Instruction> program,
                              ArrayList<InstructionDependency> deps) {
        super(loopStart, loopEnd, program, deps);
    }

    @Override
    protected boolean insertionLoop(Instruction instruction, int index) {
        Bundle bundle;

        while (index >= bundles.size()) {
            addBundle();
        }

        if (index == getLoopEndScheduledAddress()) {
            pushDownLoopEnd();
        }

        bundle = bundles.get(index);
        return bundle.insertIntoSlot(instruction);
    }

    @Override
    public void prepareLoop() {
        return;
    }


    protected void insertInterloopMov(Mov mov) {
        int lowerBound = computeInterloopDependencyResolutionLowerBoundSlot(mov);
        tryInsertFrom(mov, lowerBound, this::insertionLoop);
    }

    private int computeInterloopDependencyResolutionLowerBoundSlot(Mov mov) {
        AtomicInteger earliest = new AtomicInteger(getLoopEndScheduledAddress() - 1);
        bundles.subList(getLoopStartAddress(), Math.min(getLoopEndScheduledAddress(), bundles.size())).forEach(b ->
                b.getBundle().forEach(i -> {
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

    /* Register allocation */

    @Override
    public void allocateRegisters() {
        allocateFresh();
//        System.out.printf("Allocated fresh produced registers:\n%s\n", this);
        mapConsumed(bundles);
//        System.out.printf("Mapped consumed registers:\n%s\n", this);
        if (containsLoop()) {
            handleInterloopDependencies();
//            System.out.printf("Handled interloop dependencies:\n%s\n", this);
        }
        allocateEarlierReaders();
//        System.out.printf("Handled earlier readers:\n%s\n", this);
    }

    private void allocateFresh() {
        bundles.forEach(b ->
                b.getBundle().stream().filter(i -> i instanceof Producer).forEach(p -> {
                    int fresh = Microarchitecture.getFreshSimpleRegister();
                    ((Producer) p).setMappedDestination(fresh);
                }));
    }


    private void handleInterloopDependencies() {
        ArrayList<Mov> movs = new ArrayList<>();
        program.forEach(i ->
                dependencyMatrix.get(i.getAddress()).getInterloopDependencies().stream()
                        .filter(d -> d.getScheduledAddress() >= getLoopStartAddress())
                        .forEach(d -> {
                            int dest = getDependencySourceRegister((Consumer)i, d);
                            int cons = d.getMappedDestination();
                            Mov mov = new Mov(Math.min(bundles.size()-1, getLoopEndScheduledAddress()-1), dest, cons);
                            mov.setOperandA(cons); // mark operand as remapped
                            movs.add(mov);
                        }));
        ArrayList<Mov> distinct = movs.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Mov::toString))),
                        ArrayList::new
                ));
        distinct.forEach(this::insertInterloopMov);
        moveLoopToEnd();
    }

    private int getDependencySourceRegister(Consumer c, Producer p) {
        if (c.getOperandA() == p.getDestination())
            return c.getMappedOperandA();
        return ((DoubleConsumer) c).getMappedOperandB();
    }
}
