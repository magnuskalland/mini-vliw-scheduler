import Instructions.*;
import Microarchitecture.Microarchitecture;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class RegisterAllocator {
    private ArrayList<Instruction> program;
    private Schedule sched;
    private ArrayList<InstructionDependency> deps;
    private HashMap<Integer, Integer> map;
    static void allocate(ArrayList<Instruction> program, Schedule sched, ArrayList<InstructionDependency> deps) {
        RegisterAllocator alloc = new RegisterAllocator(program, sched, deps);
        alloc.allocateFresh();
        System.out.printf("Allocated fresh registers:\n%s\n", sched);
        alloc.mapConsumed();
        System.out.printf("Mapped consumed registers:\n%s\n", sched);
        alloc.handleInterloopDependencies();
        System.out.printf("Handled interloop dependencies:\n%s\n", sched);
        alloc.allocateEarlierReaders();
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
                    Producer producer = getProducer(c.getAddress(), ((Consumer) c).getOperandA());
                    ((Consumer) c).setOperandA(producer.getMappedDestination());

                    if (!(c instanceof DoubleConsumer))
                        return;

                    producer = getProducer(c.getAddress(), ((DoubleConsumer) c).getOperandB());
                    ((DoubleConsumer) c).setOperandB(producer.getMappedDestination());

                }));
    }

    private Producer getProducer(int consumerAddress, int register) {
        ArrayList<Producer> producers = deps.get(consumerAddress).getAll().stream()
                .filter(p -> p.getDestination() == register)
                .sorted(Comparator.comparingInt(p -> {
                    int diff = consumerAddress - p.getAddress();
                    return diff < 0 ? Integer.MIN_VALUE : diff;
                })).collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(producers);
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
                            Mov mov = new Mov(sched.getLoopEnd()-1, dest, cons);
                            movs.add(mov);
                        }));
        ArrayList<Mov> distinct = movs.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Mov::toString))),
                        ArrayList::new
                ));
        distinct.forEach(d -> sched.insert(d));
        sched.moveLoopToEnd();
    }

    private int getDependencySourceRegister(Consumer c, Producer p) {
        if (c.getOperandA() == p.getDestination())
            return c.getMappedOperandA();
        return ((DoubleConsumer) c).getMappedOperandB();
    }

    private void allocateEarlierReaders() {

    }

    private RegisterAllocator(ArrayList<Instruction> program, Schedule sched, ArrayList<InstructionDependency> deps) {
        this.program = program;
        this.sched = sched;
        this.deps = deps;
        this.map = new HashMap<>();
    }
}
