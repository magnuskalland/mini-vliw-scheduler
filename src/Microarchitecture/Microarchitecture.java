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
    private static int simpleRegister = 1;
    private static int rotatingRegister = 32;
    private static int rotatingPredicateRegister = 32;
    private final static int MAX_REGISTERS = 96;
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
