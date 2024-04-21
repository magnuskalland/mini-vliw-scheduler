import Instructions.Instruction;

import java.util.ArrayList;

public class PipelinedSchedule extends Schedule {
    PipelinedSchedule(ArrayList<Instruction> program, Instruction loopStart, Instruction loopEnd, int initiationInterval) {
        super(program, loopStart, loopEnd, initiationInterval);
    }
}
