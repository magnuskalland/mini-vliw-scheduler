import Instructions.Instruction;

import java.util.ArrayList;

public class SequentialSchedule extends Schedule {
    SequentialSchedule(ArrayList<Instruction> program, int initiationInterval, int initialLoopStart) {
        super(program, initiationInterval, initialLoopStart);
    }
}
