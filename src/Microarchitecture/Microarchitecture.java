package Microarchitecture;

import java.util.stream.IntStream;
public class Microarchitecture {
    public static final int ALU_UNITS = 2;
    public static final int MUL_UNITS = 1;
    public static final int MEM_UNITS = 1;
    public static final int BR_UNITS = 1;
    public static final int PIPELINE_WIDTH = 5;
    public static final int[] ALU_SLOTS = IntStream.rangeClosed(0, ALU_UNITS-1).toArray();
    public static final int[] MUL_SLOTS = {ALU_SLOTS[ALU_SLOTS.length - 1] + 1};
    public static final int[] MEM_SLOTS = {MUL_SLOTS[MUL_SLOTS.length - 1] + 1};
    public static final int[] BR_SLOTS = {MEM_SLOTS[MEM_SLOTS.length - 1] + 1};
    public static final int[] NOP_SLOTS = IntStream.rangeClosed(0, BR_SLOTS[BR_SLOTS.length - 1]).toArray();

    private static final int SIMPLE_REGISTER_START = 1;
    private static final int ROTATING_REGISTER_START = 32;
    private static final int ROTATING_PREDICATE_REGISTER_START = 32;
    private static int simpleRegister = SIMPLE_REGISTER_START;
    private static int rotatingRegister = ROTATING_REGISTER_START;
    private static int rotatingPredicateRegister = ROTATING_PREDICATE_REGISTER_START;
    private final static int MAX_REGISTERS = 96;

    public static void reset() {
        simpleRegister = SIMPLE_REGISTER_START;
        rotatingRegister = ROTATING_REGISTER_START;
        rotatingPredicateRegister = ROTATING_PREDICATE_REGISTER_START;
    }

    public static int getFreshSimpleRegister() {
        assert simpleRegister != MAX_REGISTERS;
        return simpleRegister++;
    }

    public static int getFreshRotatingRegister(int loopStages) {
        int tmp = rotatingRegister;
        rotatingRegister += loopStages + 1;
        if (rotatingRegister > MAX_REGISTERS)
            rotatingRegister = rotatingRegister % MAX_REGISTERS + 32;
        return tmp;
    }

    public static int getFreshRotatingPredicate() {
        return rotatingPredicateRegister++;
    }
}
