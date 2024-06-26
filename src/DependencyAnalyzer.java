import Instructions.Consumer;
import Instructions.Instruction;
import Instructions.Producer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class DependencyAnalyzer {
    private static ArrayList<Instruction> program;
    private static ArrayList<Producer> bb0, bb1, bb2;
    private static int initLoopStart, initLoopEnd;

    public static ArrayList<InstructionDependency> analyze(
            ArrayList<Instruction> instructions, int lStart, int lEnd,
            ArrayList<Producer> basicBlockZeroProducers,
            ArrayList<Producer> basicBlockOneProducers,
            ArrayList<Producer> basicBlockTwoProducers) {
        program = instructions;
        initLoopStart = lStart;
        initLoopEnd = lEnd;
        bb0 = basicBlockZeroProducers;
        bb1 = basicBlockOneProducers;
        bb2 = basicBlockTwoProducers;

        ArrayList<InstructionDependency> dependencyMatrix = new ArrayList<>();

        for (Instruction i : program) {
            InstructionDependency dependency = new InstructionDependency(i.getAddress());
            if (i instanceof Producer)
                dependency.setDestination(((Producer) i).getDestination());
            dependencyMatrix.add(dependency);
        }

        program.stream().filter(Instruction::isTrueConsumer).forEach(instruction -> {
            Consumer consumer = (Consumer) instruction;
            identifyLocalDependencies(consumer).forEach(d ->
                    dependencyMatrix.get(consumer.getAddress()).addLocalDependency(d));
            identifyInterloopDependencies(consumer).forEach(d ->
                    dependencyMatrix.get(consumer.getAddress()).addInterloopDependency(d));
            identifyLoopInvariantDependencies(consumer).forEach(d ->
                    dependencyMatrix.get(consumer.getAddress()).addLoopInvariantDependency(d));
            identifyPostLoopDependencies(consumer).forEach(d ->
                    dependencyMatrix.get(consumer.getAddress()).addPostLoopDependency(d));
        });

//        System.out.printf("     %-30s   %-30s   %-30s   %-30s\n",
//                "Local dependencies", "Interloop dependencies", "Loop invariant", "Post loop dependencies");
//        for (InstructionDependency d : dependencyMatrix)
//            System.out.printf("%s\n", d);
//        System.out.println();
        return dependencyMatrix;
    }

    private static ArrayList<Producer> identifyLocalDependencies(Consumer consumer) {
        ArrayList<Producer> deps = new ArrayList<>();
        int start = getBasicBlockOfInstructionStart(consumer);
        int end = getBasicBlockOfInstructionEnd(consumer);

        BiConsumer<List<Producer>, List<Producer>> addDependencies = (p, d) -> p.stream()
                .filter(i ->
                        i.getAddress() < consumer.getAddress() &&
                                i.getAddress() >= start &&
                                i.getAddress() < end)
                .filter(consumer::isDependentOn)
                .forEach(d::add);

        // Only one of these calls will add instructions as dependencies
        addDependencies.accept(bb0, deps);
        addDependencies.accept(bb1, deps);
        addDependencies.accept(bb2, deps);
        return deps;
    }

    private static ArrayList<Producer> identifyInterloopDependencies(Consumer consumer) {
        ArrayList<Producer> deps = new ArrayList<>();

        // Consumer must be in loop body
        if (consumer.getAddress() < initLoopStart || consumer.getAddress() >= initLoopEnd)
            return deps;

        bb0.stream()
                .filter(consumer::isDependentOn)
                .forEach(deps::add);

        bb1.stream()
                .filter(consumer::isDependentOn)
                .filter(i -> i.getAddress() >= consumer.getAddress())
                .forEach(deps::add);

        // Filter out loop invariant dependencies
        BiPredicate<Instruction, Instruction> matchesCondition = (i, o) ->
                i != o &&
                        ((i.getAddress() < initLoopStart && o.getAddress() >= initLoopStart) ||
                                (i.getAddress() >= initLoopStart && o.getAddress() < initLoopStart)) &&
                        ((Producer)i).getDestination() == ((Producer)o).getDestination();

        return deps.stream()
                .filter(i -> deps.stream()
                        .anyMatch(o -> matchesCondition.test(i, o)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static ArrayList<Producer> identifyLoopInvariantDependencies(Consumer consumer) {
        ArrayList<Producer> deps = new ArrayList<>();

        // Consumer must be in loop body
        if (consumer.getAddress() < initLoopStart || consumer.getAddress() >= initLoopEnd)
            return deps;

        bb0.stream()
                .filter(consumer::isDependentOn)
                .filter(i -> bb1.stream().allMatch(p -> i.getDestination() != p.getDestination()))
                .forEach(deps::add);

        return deps;
    }

    private static ArrayList<Producer> identifyPostLoopDependencies(Consumer consumer) {
        ArrayList<Producer> deps = new ArrayList<>();

        if (consumer.getAddress() < initLoopEnd)
            return deps;

        bb1.stream()
                .filter(consumer::isDependentOn)
                .forEach(deps::add);

        return deps;
    }

    private static int getBasicBlockOfInstructionStart(Instruction instruction) {
        int address = instruction.getAddress();
        Integer[] candidates = { 0, initLoopStart, initLoopEnd};
        Integer[] difference = { address, address - initLoopStart, address - initLoopEnd};
        return candidates[Arrays.asList(difference).indexOf(Arrays.stream(difference)
                .map(i -> i >= 0 ? i : Integer.MAX_VALUE)
                .min(Integer::compare).get())];
    }

    private static int getBasicBlockOfInstructionEnd(Instruction instruction) {
        int address = instruction.getAddress();
        Integer[] candidates = {initLoopStart, initLoopEnd, program.size() };
        Integer[] difference = { initLoopStart - address, initLoopEnd - address, program.size() - address };
        return candidates[Arrays.asList(difference).indexOf(Arrays.stream(difference)
                .map(i -> i > 0 ? i : Integer.MAX_VALUE)
                .min(Integer::compare).get())];
    }
}
