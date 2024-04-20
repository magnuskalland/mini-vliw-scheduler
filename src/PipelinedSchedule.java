import Instructions.Instruction;

import java.util.ArrayList;

public class PipelinedSchedule extends Schedule {
    PipelinedSchedule(ArrayList<Instruction> program, int initiationInterval) {
        super(program, initiationInterval, 0);
    }
}
