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

        if (index == getLoopEndAddress()) {
            pushDownLoopEnd();
        }

        bundle = bundles.get(index);
        return bundle.insertIntoSlot(instruction);
    }


    protected void insertInterloopMov(Mov mov) {
        int lowerBound = computeInterloopDependencyResolutionLowerBoundSlot(mov);
        tryInsertFrom(mov, lowerBound, this::insertionLoop);
    }

    private int computeInterloopDependencyResolutionLowerBoundSlot(Mov mov) {
        AtomicInteger earliest = new AtomicInteger(getLoopEndAddress() - 1);
        bundles.subList(getLoopStartAddress(), Math.min(getLoopEndAddress(), bundles.size())).forEach(b ->
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
        System.out.printf("Allocated fresh produced registers:\n%s\n", this);
        mapConsumed();
        System.out.printf("Mapped consumed registers:\n%s\n", this);
        if (containsLoop()) {
            handleInterloopDependencies();
            System.out.printf("Handled interloop dependencies:\n%s\n", this);
        }
        allocateEarlierReaders();
        System.out.printf("Handled earlier readers:\n%s\n", this);
    }

    private void allocateFresh() {
        bundles.forEach(b ->
                b.getBundle().stream().filter(i -> i instanceof Producer).forEach(p -> {
                    int fresh = Microarchitecture.getFreshSimpleRegister();
                    ((Producer) p).setMappedDestination(fresh);
                }));
    }

    private void mapConsumed() {
        bundles.forEach(b ->
                b.getBundle().stream()
                        .filter(i -> i.isTrueConsumer() && !dependencyMatrix.get(i.getAddress()).getAll().isEmpty())
                        .forEach(c -> {

                            if (dependencyMatrix.get(c.getAddress()).getAll().stream().anyMatch(d -> d.getDestination() == ((Consumer) c).getOperandA())) {
                                Producer producer = getMostRecentProducer(c.getAddress(), ((Consumer) c).getOperandA());
                                ((Consumer) c).setOperandA(producer.getMappedDestination());
                            }

                            if (!(c instanceof DoubleConsumer))
                                return;

                            if (dependencyMatrix.get(c.getAddress()).getAll().stream().anyMatch(d -> d.getDestination() == ((DoubleConsumer) c).getOperandB())) {
                                Producer producer = getMostRecentProducer(c.getAddress(), ((DoubleConsumer) c).getOperandB());
                                ((DoubleConsumer) c).setOperandB(producer.getMappedDestination());
                            }
                        }));
    }

    private Producer getMostRecentProducer(int consumerAddress, int register) {
        ArrayList<Producer> producers = dependencyMatrix.get(consumerAddress).getAll().stream()
                .filter(p -> p.getDestination() == register)
                .sorted(Comparator.comparingInt(p -> {
                    int diff = consumerAddress - p.getAddress();
                    return diff <= 0 ? Integer.MAX_VALUE : diff;
                }))
                .collect(Collectors.toCollection(ArrayList::new));
        return producers.get(0);
    }

    private void handleInterloopDependencies() {
        ArrayList<Mov> movs = new ArrayList<>();
        program.forEach(i ->
                dependencyMatrix.get(i.getAddress()).getInterloopDependencies().stream()
                        .filter(d -> d.getScheduledAddress() >= getLoopStartAddress())
                        .forEach(d -> {
                            int dest = getDependencySourceRegister((Consumer)i, d);
                            int cons = d.getMappedDestination();
                            Mov mov = new Mov(Math.min(bundles.size()-1, getLoopEndAddress()-1), dest, cons);
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

    private void allocateEarlierReaders() {
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
}
