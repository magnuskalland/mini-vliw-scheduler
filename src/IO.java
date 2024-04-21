import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import Instructions.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

public class IO {

    static ArrayList<Instruction> parseInstructions(String inputPath) {
        ArrayList<Instruction> instructions = new ArrayList<>();

        try {
            String content = new String(Files.readAllBytes(Paths.get(inputPath)));
            Gson gson = new Gson();
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> instructionList = gson.fromJson(content, listType);
            int i = 0;
            for (String instruction : instructionList) {
                try {
                    instructions.add(parseInstruction(instruction.replace(",", ""), i++));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        return instructions;
    }

    static void dump(Schedule schedule, String path) {
        Gson gson = new Gson();

        // Some copiloting to manually format the document
        StringBuilder sb = new StringBuilder();
        sb.append("[\n\t");

        for (Bundle b : schedule.get()) {
            ArrayList<String> list = new ArrayList<>();
            for (Instruction i : b.get())
                list.add(i.toString());
            sb.append(gson.toJson(list)).append(",\n\t");
        }

        // Remove the last comma and newline, and add the closing bracket
        if (sb.length() > 2)
            sb.setLength(sb.length() - 3);
        sb.append("\n]");

        try (FileWriter writer = new FileWriter(path)) {
            writer.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Instruction parseInstruction(String instruction, int index) throws Exception {
        String[] vec = instruction.split(" ");
        vec[1] = vec[1]
                .replaceFirst("x", "")
                .replace("(", " ")
                .replace(")", "");

        if (vec[0].equals("mov")) {
            return vec[1].charAt(0) == 'p' ?
                        // Move predicate
                        new MovP(index, Integer.parseInt(vec[1]), vec[2].replaceFirst("p", ""))
                    : vec[1].equals("LC") || vec[1].equals("EC") ?
                        // Move loop
                        new MovLoop(index, vec[1], Integer.parseInt(vec[2]))
                    : vec[2].charAt(0) == 'x' ?
                        // Move
                        new Mov(index, Integer.parseInt(vec[1]), Integer.parseInt(vec[2].replaceFirst("x", "")))
                    // Move immediate
                    : new Movi(index, Integer.parseInt(vec[1]), vec[2]);
        }

        if (vec[0].equals("addi")) {
            return new Addi(index, Integer.parseInt(vec[1]), Integer.parseInt(vec[2].replaceFirst("x", "")), vec[3]);
        }
        if (vec[0].equals("st")) {
            String[] mem = vec[2].replace("(", " ").replace(")", "").split(" ");
            return new St(index, Integer.parseInt(vec[1]), mem[0], Integer.parseInt(mem[1].replaceFirst("x", "")));
        }
        if (vec[0].equals("ld")) {
            String[] mem = vec[2].replace("(", " ").replace(")", "").split(" ");
            return new Ld(index, Integer.parseInt(vec[1]), mem[0], Integer.parseInt(mem[1].replaceFirst("x", "")));
        }

        // No immediates left at this point, so we can safely remove the register prefix 'x'

        vec = Arrays.stream(vec).map(s -> s.replace("x", "")).toArray(String[]::new);
        return switch(vec[0]) {
            case "add" -> new Add(index, Integer.parseInt(vec[1]), Integer.parseInt(vec[2]), Integer.parseInt(vec[3]));
            case "sub" -> new Sub(index, Integer.parseInt(vec[1]), Integer.parseInt(vec[2]), Integer.parseInt(vec[3]));
            case "mulu" ->
                    new Mulu(index, Integer.parseInt(vec[1]), Integer.parseInt(vec[2]), Integer.parseInt(vec[3]));
            case "loop" -> new Loop(index, Integer.parseInt(vec[1]));
            case "loop.pip" -> new LoopPip(index, Integer.parseInt(vec[1]));
            case "nop" -> new Nop(index, -1);
            default ->
                throw new Exception(String.format("Parsing instruction '%s' not implemented", vec[1]));
        };
    }
}
