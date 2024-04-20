// bash build.sh && bash run.sh given_tests/01/input.json given_tests/01/user_output.json

// Test all:
// sudo bash runall.sh && sudo bash testall.sh

import Instructions.Instruction;

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
        ArrayList<Instruction> instructions = IO.parseInstructions(input);

        Schedule simple = Scheduler.schedule(instructions, false);
        IO.dump(simple, simpleOutput);

//        Schedule pipelined = Scheduler.schedule(instructions, true);
//        IO.dump(pipelined, pipOutput);
    }
}
