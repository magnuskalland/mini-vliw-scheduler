import Instructions.*;
import Microarchitecture.Microarchitecture;

import java.util.*;
import java.util.stream.Collectors;

public class RegisterAllocator {
    private final ArrayList<Instruction> program;
    private final Schedule sched;
    private final ArrayList<InstructionDependency> deps;
    static void allocate(ArrayList<Instruction> program, Schedule sched, ArrayList<InstructionDependency> deps) {
        RegisterAllocator alloc = new RegisterAllocator(program, sched, deps);
        alloc.allocateFresh();
        System.out.printf("Allocated fresh produced registers:\n%s\n", sched);
        alloc.mapConsumed();
        System.out.printf("Mapped consumed registers:\n%s\n", sched);
        alloc.handleInterloopDependencies();
        System.out.printf("Handled interloop dependencies:\n%s\n", sched);
        alloc.allocateEarlierReaders();
        System.out.printf("Handled earlier readers:\n%s\n", sched);
    }

    private void allocateFresh() {
        sched.get().forEach(b ->
                b.get().stream().filter(i -> i instanceof Producer).forEach(p -> {
                    int fresh = Microarchitecture.getFreshSimpleRegister();
                    ((Producer) p).setMappedDestination(fresh);
                }));
    }

    private void mapConsumed() {
        sched.get().forEach(b ->
                b.get().stream().filter(Instruction::isTrueConsumer).forEach(c -> {
                    Producer producer = getMostRecentProducer(c.getAddress(), ((Consumer) c).getOperandA());
                    ((Consumer) c).setOperandA(producer.getMappedDestination());

                    if (!(c instanceof DoubleConsumer))
                        return;

                    producer = getMostRecentProducer(c.getAddress(), ((DoubleConsumer) c).getOperandB());
                    ((DoubleConsumer) c).setOperandB(producer.getMappedDestination());

                }));
    }

    private Producer getMostRecentProducer(int consumerAddress, int register) {
        ArrayList<Producer> producers = deps.get(consumerAddress).getAll().stream()
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
                deps.get(i.getAddress()).getInterloopDependencies().stream()
                        .filter(d -> d.getScheduledAddress() >= sched.getLoopStart())
                        .forEach(d -> {
                            int dest = getDependencySourceRegister((Consumer)i, d);
                            int cons = d.getMappedDestination();
                            Mov mov = new Mov(Math.min(sched.get().size()-1, sched.getLoopEnd()-1), dest, cons);
                            mov.setOperandA(cons); // mark operand as remapped
                            movs.add(mov);
                        }));
        ArrayList<Mov> distinct = movs.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Mov::toString))),
                        ArrayList::new
                ));
        distinct.forEach(sched::insert);
        sched.moveLoopToEnd();
    }

    private int getDependencySourceRegister(Consumer c, Producer p) {
        if (c.getOperandA() == p.getDestination())
            return c.getMappedOperandA();
        return ((DoubleConsumer) c).getMappedOperandB();
    }

    private void allocateEarlierReaders() {
        sched.get().forEach(b ->
                b.get().stream()
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

    private RegisterAllocator(ArrayList<Instruction> program, Schedule sched, ArrayList<InstructionDependency> deps) {
        this.program = program;
        this.sched = sched;
        this.deps = deps;
    }
}
