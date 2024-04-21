import Instructions.Instruction;

import java.util.ArrayList;

public class SequentialSchedule extends Schedule {
    SequentialSchedule(ArrayList<Instruction> program, Instruction loopStart, Instruction loopEnd, int initiationInterval) {
        super(program, loopStart, loopEnd, initiationInterval);
    }
}
