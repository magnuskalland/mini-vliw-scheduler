import Instructions.Instruction;
import Instructions.Producer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InstructionDependency {
    private final int address;
    private int destination;
    private final ArrayList<Producer> localDependencies;
    private final ArrayList<Producer> interloopDependencies;
    private final ArrayList<Producer> loopInvariantDependencies;
    private final ArrayList<Producer> postLoopDependencies;

    InstructionDependency(int address) {
        this.address = address;
        localDependencies = new ArrayList<>();
        interloopDependencies = new ArrayList<>();
        loopInvariantDependencies = new ArrayList<>();
        postLoopDependencies = new ArrayList<>();
    }
    ArrayList<Producer> getAll() {
        return (ArrayList<Producer>) Stream.of(
                        localDependencies,
                        interloopDependencies,
                        loopInvariantDependencies,
                        postLoopDependencies)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
    void setDestination(int destination) {
        this.destination = destination;
    }
    int getDestination() {
        return destination;
    }
    void addLocalDependency(Producer producer) {
        localDependencies.add(producer);
    }
    ArrayList<Producer> getLocalDependencies() {
        return localDependencies;
    }
    void addInterloopDependency(Producer producer) {
        interloopDependencies.add(producer);
    }
    ArrayList<Producer> getInterloopDependencies() {
        return interloopDependencies;
    }
    void addLoopInvariantDependency(Producer producer) {
        loopInvariantDependencies.add(producer);
    }
    ArrayList<Producer> getLoopInvariantDependencies() {
        return loopInvariantDependencies;
    }
    void addPostLoopDependency(Producer producer) {
        postLoopDependencies.add(producer);
    }
    ArrayList<Producer> getPostLoopDependencies() {
        return postLoopDependencies;
    }

    @Override
    public String toString() {
        Function<List<Producer>, String> formatDependencies = deps ->
                deps.isEmpty() ? "" : Arrays.toString(deps.stream()
                        .map(i -> String.format("(%d, x%d)", i.getAddress(), i.getDestination()))
                        .toArray());

        return String.format("%-2d | %20s | %20s | %20s | %20s", address,
                formatDependencies.apply(localDependencies),
                formatDependencies.apply(interloopDependencies),
                formatDependencies.apply(loopInvariantDependencies),
                formatDependencies.apply(postLoopDependencies));
    }
}
