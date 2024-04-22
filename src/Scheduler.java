import Instructions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Scheduler {
    private final ArrayList<Instruction> program;
    private final Instruction loopStart, loopEnd;

    public static Schedule schedule(ArrayList<Instruction> program, boolean pipelined) {
        Instruction loopStart, loopEnd;
        ArrayList<InstructionDependency> deps;
        Schedule sched;
        Scheduler scheduler;

        scheduler = new Scheduler(program);
        loopStart = scheduler.getLoopStart();
        loopEnd = scheduler.getLoopEnd();

        deps = DependencyAnalyzer.analyze(program,
                scheduler.getInitialLoopStartAddress(),
                scheduler.getInitialLoopEndAddress(),
                scheduler.getBasicBlockZeroProducers(),
                scheduler.getBasicBlockOneProducers(),
                scheduler.getBasicBlockTwoProducers());

        sched = pipelined ?
                new PipelinedSchedule(loopStart, loopEnd, program, deps) :
                new SequentialSchedule(loopStart, loopEnd, program, deps);

        System.out.printf("%s\n", scheduler.getInitialProgram(sched));

        for (Instruction i : program)
            sched.scheduleInstruction(i);

        System.out.println("\nInitial schedule:");
        System.out.printf("%s\n", sched);

        sched.allocateRegisters();
        sched.prepareLoop();

        return sched;
    }

    private Scheduler(ArrayList<Instruction> program) {
        this.program = program;
        this.loopStart = defineLoopStart(program);
        this.loopEnd = defineLoopEnd(program);
    }

    private Instruction getLoopStart() {
        return loopStart;
    }

    private Instruction getLoopEnd() {
        return loopEnd;
    }

    private Instruction defineLoopStart(ArrayList<Instruction> program) {
        Branch branch = (Branch) program.stream()
                .filter(instruction -> instruction instanceof Branch)
                .findFirst().orElse(null);
        return branch == null ? null : program.get(branch.getTarget());
    }

    private Instruction defineLoopEnd(ArrayList<Instruction> program) {
        Branch branch = (Branch) program.stream()
                .filter(instruction -> instruction instanceof Branch)
                .findFirst().orElse(null);
        try {
            return branch == null ? null : program.get(branch.getAddress() + 1);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private String getInitialProgram(Schedule sched) {
        AtomicInteger i = new AtomicInteger(0);
        String programString = program.stream()
                .map(instr -> {
                    int index = i.getAndIncrement();
                    return (index == getInitialLoopStartAddress() ? "BB1:" : "") +
                            (index == getInitialLoopEndAddress() ? "BB2:" : "") +
                            String.format("\t0x%x: %s\n", index, instr);
                })
                .collect(Collectors.joining());
        return "BB0:" + programString + String.format("Initial II: %d\n", sched.getInitiationInterval());
    }

    private ArrayList<Instruction> getBasicBlockZero() {
        return program.stream().filter(i -> i.getAddress() < getInitialLoopStartAddress())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArrayList<Instruction> getBasicBlockOne() {
        return program.stream().filter(i -> i.getAddress() >= getInitialLoopStartAddress() &&
                    i.getAddress() < getInitialLoopEndAddress())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArrayList<Instruction> getBasicBlockTwo() {
        return program.stream().filter(i -> i.getAddress() >= getInitialLoopEndAddress())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArrayList<Producer> getProducersFromBasicBlock(List<Instruction> basicBlock) {
        return basicBlock.stream()
                .filter(i -> i instanceof Producer)
                .map(p -> (Producer) p)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArrayList<Producer> getBasicBlockZeroProducers() {
        return getProducersFromBasicBlock(getBasicBlockZero());
    }

    private ArrayList<Producer> getBasicBlockOneProducers() {
        return getProducersFromBasicBlock(getBasicBlockOne());
    }

    private ArrayList<Producer> getBasicBlockTwoProducers() {
        return getProducersFromBasicBlock(getBasicBlockTwo());
    }

    private int getInitialLoopStartAddress() {
        return loopStart == null ? program.size() : loopStart.getAddress();
    }

    private int getInitialLoopEndAddress() {
        return loopEnd == null ? program.size() : loopEnd.getAddress();
    }
}
