import Instructions.Instruction;
import Microarchitecture.Microarchitecture;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Example execution with run scripts:
 * ./build.sh && ./run.sh given_tests/17/input.json given_tests/17/pip_user.json given_tests/17/simple_user.json
 */

public class Main {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.print("usage: java Main <path-to-input.json> <path-to-loop.json (output)> <path-to-looppip.json (output)>\n");
            System.exit(0);
        }

        String input = args[0], simpleOutput = args[1], pipOutput = args[2];

        Schedule simple = Scheduler.schedule(IO.parseInstructions(input), false);
        IO.dump(simple, simpleOutput);

        Microarchitecture.reset();

        Schedule pipelined = Scheduler.schedule(IO.parseInstructions(input), true);
        IO.dump(pipelined, pipOutput);
    }
}
