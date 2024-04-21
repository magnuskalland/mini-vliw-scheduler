import Instructions.*;
import Microarchitecture.Microarchitecture;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Scheduler {
    private Instruction loopStart, loopEnd;
    int initiationInterval, loopStages;
    ArrayList<Instruction> program;

    public static Schedule schedule(ArrayList<Instruction> instructions, boolean pipelined) {
        Scheduler scheduler = new Scheduler();
        Schedule schedule;
        ArrayList<InstructionDependency> dependencies;

        scheduler.program = instructions;
        scheduler.defineBasicBlocks();
        scheduler.initiationInterval = scheduler.computeInitiationIntervalLowerBound();
        System.out.printf("%s\n", scheduler.getInitialProgram());

        while (true) {
            dependencies = DependencyAnalyzer.analyze(instructions, scheduler.getLoopStartAddress(), scheduler.getLoopEndAddress(),
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
        Schedule schedule = new PipelinedSchedule(program, loopStart, loopEnd, initiationInterval);
        for (Instruction i : getBasicBlockZero()) {
            schedule.insert(i, dependencies.get(i.getAddress()), initiationInterval);
        }
        return schedule;
    }

    public Schedule scheduleSequential(ArrayList<Instruction> program, ArrayList<InstructionDependency> dependencies) {
        Schedule schedule = new SequentialSchedule(program, loopStart, loopEnd, initiationInterval);

        for (Instruction i : program) {
            schedule.insert(i, dependencies.get(i.getAddress()), initiationInterval);
        }

        System.out.println("Initial schedule:");
        System.out.printf("%s\n", schedule);
        return schedule;
    }

    private int computeInitiationIntervalLowerBound() {
        int[] counts = new int[Microarchitecture.PIPELINE_WIDTH];
        program.subList(getLoopStartAddress(), getLoopEndAddress())
                .forEach(i -> counts[i.getPipelineSlots()[0]]++);
        counts[0] = (int) Math.ceil((double) counts[0] / Microarchitecture.ALU_UNITS);
        return Arrays.stream(counts).max().getAsInt();
    }

    private void defineBasicBlocks() {
        Branch branch = (Branch) program.stream()
                .filter(instruction -> instruction instanceof Branch)
                .findFirst().orElse(null);
        loopStart = branch == null ? null : program.get(branch.getTarget());
        try {
            loopEnd = branch == null ? null : program.get(branch.getAddress() + 1);
        } catch (IndexOutOfBoundsException e) {
            loopEnd = null;
        }
        System.out.printf("Loop start is %s and end is %s\n", loopStart, loopEnd);
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
                    return (index == getLoopStartAddress() ? "BB1:" : "") +
                            (index == getLoopEndAddress() ? "BB2:" : "") +
                            String.format("\t0x%x: %s\n", index, instr);
                })
                .collect(Collectors.joining());

        return "BB0:" + programString + String.format("Initial II: %d\n", computeInitiationIntervalLowerBound());
    }
    private ArrayList<Instruction> getBasicBlockZero() {
        return program.stream().filter(i -> i.getAddress() < getLoopStartAddress())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArrayList<Instruction> getBasicBlockOne() {
        return program.stream().filter(i -> i.getAddress() >= getLoopStartAddress() &&
                    i.getAddress() < getLoopEndAddress())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArrayList<Instruction> getBasicBlockTwo() {
        return program.stream().filter(i -> i.getAddress() >= getLoopEndAddress())
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

    private int getLoopStartAddress() {
        return loopStart == null ? program.size() : loopStart.getAddress();
    }

    private int getLoopEndAddress() {
        return loopEnd == null ? program.size() : loopEnd.getAddress();
    }
}
