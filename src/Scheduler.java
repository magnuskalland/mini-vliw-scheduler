import Instructions.*;
import Microarchitecture.Microarchitecture;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Scheduler {
    private int initialLoopStart, initialLoopEnd;
    int initiationInterval, loopStages;
    ArrayList<Instruction> program;

    public static Schedule schedule(ArrayList<Instruction> instructions, boolean pipelined) {
        Scheduler scheduler = new Scheduler();
        Schedule schedule;
        ArrayList<InstructionDependency> dependencies;

        scheduler.program = instructions;
        scheduler.defineInitialBasicBlocks();
        scheduler.initiationInterval = scheduler.computeInitiationIntervalLowerBound();
        System.out.printf("%s\n", scheduler.getInitialProgram());

        while (true) {
            dependencies = DependencyAnalyzer.analyze(instructions, scheduler.initialLoopStart, scheduler.initialLoopEnd,
                    scheduler.getBasicBlockZeroProducers(),
                    scheduler.getBasicBlockOneProducers(),
                    scheduler.getBasicBlockTwoProducers());

            if (pipelined) {
                schedule = scheduler.schedulePipelined(instructions, dependencies);
                break;
            }

            schedule = scheduler.scheduleSequential(instructions, dependencies);
            RegisterAllocator.allocate(instructions, schedule, dependencies);
            break;
        }
        return schedule;
    }

    public Schedule schedulePipelined(ArrayList<Instruction> program, ArrayList<InstructionDependency> dependencies) {
        Schedule schedule = new PipelinedSchedule(program, initiationInterval);

        return schedule;
    }

    public Schedule scheduleSequential(ArrayList<Instruction> program, ArrayList<InstructionDependency> dependencies) {
        Schedule schedule = new SequentialSchedule(program, initiationInterval, initialLoopStart);

        for (Instruction i : getBasicBlockZero()) {
            schedule.insert(i, dependencies.get(i.getAddress()), initiationInterval);
        }

        for (Instruction i : getBasicBlockOne()) {
            schedule.insert(i, dependencies.get(i.getAddress()), initiationInterval);
        }

        for (Instruction i : getBasicBlockTwo()) {
            schedule.insert(i, dependencies.get(i.getAddress()), initiationInterval);
        }

        System.out.printf("%s\n", schedule);
        return schedule;
    }

    private int computeInitiationIntervalLowerBound() {
        int[] counts = new int[Microarchitecture.PIPELINE_WIDTH];
        program.subList(initialLoopStart, initialLoopEnd).forEach(i -> counts[i.getPipelineSlots()[0]]++);
        counts[0] = (int) Math.ceil((double) counts[0] / Microarchitecture.ALU_UNITS);
        return Arrays.stream(counts).max().getAsInt();
    }

    private void defineInitialBasicBlocks() {
        Optional<Instruction> loop = program.stream()
                .filter(instruction -> instruction instanceof Branch)
                .findFirst();
        initialLoopStart = loop.map(instruction -> ((Branch) instruction).getTarget()).orElse(program.size());
        initialLoopEnd = loop.map(instruction -> instruction.getAddress() + 1).orElse(program.size());
    }

    private boolean verifyInterloopDependencyInvariant(ArrayList<InstructionDependency> dependencies) {
        for (Instruction i : program) {
            for (Instruction d : dependencies.get(i.getAddress()).getInterloopDependencies()) {
                if (!interloopDependencyInvariant(d, i, initiationInterval)) {
                    System.out.printf("Invariant failed for %d %s --> %d %s\n", i.getScheduledAddress(), i, d.getScheduledAddress(), d);
                    return false;
                }
            }
        }
        return true;
    }

    private boolean interloopDependencyInvariant(Instruction p, Instruction c, int initiationInterval) {
        return p.getScheduledAddress() + p.getLatency() <= c.getScheduledAddress() + initiationInterval;
    }

    private String getInitialProgram() {
        AtomicInteger i = new AtomicInteger(0);
        String programString = program.stream()
                .map(instr -> {
                    int index = i.getAndIncrement();
                    return (index == initialLoopStart ? "BB1:" : "") +
                            (index == initialLoopEnd ? "BB2:" : "") +
                            String.format("\t0x%x: %s\n", index, instr);
                })
                .collect(Collectors.joining());

        return "BB0:" + programString + String.format("Initial II: %d\n", computeInitiationIntervalLowerBound());
    }
    private ArrayList<Instruction> getBasicBlockZero() {
        return program.stream().filter(i -> i.getAddress() < initialLoopStart)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArrayList<Instruction> getBasicBlockOne() {
        return program.stream().filter(i -> i.getAddress() >= initialLoopStart
                    && i.getAddress() < initialLoopEnd)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArrayList<Instruction> getBasicBlockTwo() {
        return program.stream().filter(i -> i.getAddress() >= initialLoopEnd)
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
}
