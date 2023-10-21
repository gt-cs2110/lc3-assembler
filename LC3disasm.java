import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.List;
import java.util.Optional;

/**
 * Disassembler: Attempts to reconstruct assembly from object files produced by
 * LC3asm.java.
 *
 * Invoking this with `java LC3disasm bubba.obj' will produce a file
 * `bubba.dis.asm' which attempts to decode instructions into assembly syntax
 * if possible. Pass `-x' on the command line to write hex .fills for words
 * that can't be decoded as instructions instead of writing their
 * interpretation in 16-bit two's complement.
 *
 * Author: Austin J. Adams IV, B.S., M.S., B.F.D
 */
public class LC3disasm {
    public static void main(String[] args) {
        // Tedious argument parsing
        String objfilename = null;
        boolean hexFillsArg = false;
        if (args.length == 1) {
            objfilename = args[0];
        } else if (args.length == 2 && (args[0].equals("-x") ^ args[1].equals("-x"))) {
            hexFillsArg = true;
            objfilename = args[args[1].equals("-x")? 0 : 1];
        } else {
            System.err.println("usage: java LC3disasm [-x] <path to object file>");
            System.exit(1);
        }
        final boolean useHexFills = hexFillsArg;

        String filebase = objfilename.substring(0, objfilename.lastIndexOf('.')); // get the root filename without any extensions
        File objFile = new File(objfilename);
        File disasmFile = new File(filebase + ".dis.asm");

        List<OrigEndWindow> origBlocks = parseObjectFile(objFile);

        try (PrintStream dis = new PrintStream(disasmFile)) {
            boolean first = true;
            for (OrigEndWindow window : origBlocks) {
                if (!first) {
                    dis.println(); // Blank line between .end and next .orig
                }
                first = false;

                dis.printf(".orig x%04x%n", window.origAddr);

                window.words
                      .stream()
                      .map(word -> Instruction.decodeOrFill(word, useHexFills))
                      .forEach(dis::println);

                dis.println(".end");
            }
        } catch (IOException err) {
            // Rethrow as unchecked (Thank you Mr. Dr. Gosling sir)
            throw new RuntimeException(err);
        }

        System.out.println("Wrote disassembly to " + disasmFile.getPath());
    }

    private static List<OrigEndWindow> parseObjectFile(File objFile) {
        final String ORIG = "ORIG: ";
        List<OrigEndWindow> windows = new ArrayList<OrigEndWindow>();

        try (FileReader fr = new FileReader(objFile);
             BufferedReader br = new BufferedReader(fr)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                boolean isOrig = false;
                if ((isOrig = line.startsWith(ORIG))) {
                    line = line.substring(ORIG.length());
                }
                if (line.startsWith("x")) {
                    line = line.substring(1);
                }
                Integer value = Integer.parseInt(line, 16);

                if (isOrig) {
                    windows.add(new OrigEndWindow(value));
                } else {
                    if (windows.isEmpty()) {
                        throw new IllegalStateException("Word before .orig");
                    } else {
                        OrigEndWindow currentBlock = windows.get(windows.size()-1);
                        currentBlock.words.add(value);
                    }
                }
            }
        } catch (IOException err) {
            // Rethrow as unchecked (Thank you Mr. Dr. Gosling sir)
            throw new RuntimeException(err);
        }

        return windows;
    }

    static class OrigEndWindow {
        int origAddr;
        List<Integer> words;

        OrigEndWindow(int origAddr) {
            this(origAddr, new ArrayList<Integer>());
        }

        OrigEndWindow(int origAddr, List<Integer> words) {
            this.origAddr = origAddr;
            this.words = words;
        }
    }

    static enum Instruction {
        // You can look at the following as a list of rules. The first line is
        // the condition that the word w passed in can be interpreted as a
        // given instruction. Then the second line formats it in assembly
        // syntax.

        ADD (w -> (w >> 12 & 0xf) == 0b0001 && (w >> 3 & 0x7) == 0b000,
             w -> String.format("add r%d, r%d, r%d", w >> 9 & 0x7, w >> 6 & 0x7, w & 0x7)),

        ADDI(w -> (w >> 12 & 0xf) == 0b0001 && (w & 0x1 << 5) != 0,
             w -> String.format("add r%d, r%d, %d", w >> 9 & 0x7, w >> 6 & 0x7, sext(w & 0x1f, 5))),

        AND (w -> (w >> 12 & 0xf) == 0b0101 && (w >> 3 & 0x7) == 0b000,
             w -> String.format("and r%d, r%d, r%d", w >> 9 & 0x7, w >> 6 & 0x7, w & 0x7)),

        ANDI(w -> (w >> 12 & 0xf) == 0b0101 && (w & 0x1 << 5) != 0,
             w -> String.format("and r%d, r%d, %d", w >> 9 & 0x7, w >> 6 & 0x7, sext(w & 0x1f, 5))),

        // This works fine but is confusing in my opinion because currently the
        // assembler outputs 0x0000 for .blkw 1, which then shows up here as a nop
        //NOP (w -> (w >> 12 & 0xf) == 0b0000 && (w >> 9 & 0x7) == 0b000,
        //     w -> "nop"),

        BR  (w -> (w >> 12 & 0xf) == 0b0000 && (w >> 9 & 0x7) != 0b000,
             w -> String.format("br%s%s%s %d", (((w >> 11 & 0x1) == 0)? "" : "n"),
                                               (((w >> 10 & 0x1) == 0)? "" : "z"),
                                               (((w >> 9 & 0x1) == 0)? "" : "p"),
                                               sext(w & 0x1ff, 9))),

        JMP (w -> (w >> 12 & 0xf) == 0b1100 && (w >> 9 & 0x7) == 0 && (w >> 6 & 0x7) != 7 && (w & 0x3f) == 0,
             w -> String.format("jmp r%d", w >> 6 & 0x7)),

        JSR (w -> (w >> 12 & 0xf) == 0b0100 && (w >> 11 & 0x1) == 1,
             w -> String.format("jsr %d", sext(w & 0x7FF, 11))),

        JSRR(w -> (w >> 12 & 0xf) == 0b0100 && (w >> 9 & 0x1) == 0 && (w & 0x3f) == 0,
             w -> String.format("jsrr r%d", w >> 6 & 0x7)),

        LD  (w -> (w >> 12 & 0xf) == 0b0010,
             w -> String.format("ld r%d, %d", w >> 9 & 0x7, sext(w & 0x1ff, 9))),

        LDI (w -> (w >> 12 & 0xf) == 0b1010,
             w -> String.format("ldi r%d, %d", w >> 9 & 0x7, sext(w & 0x1ff, 9))),

        LDR (w -> (w >> 12 & 0xf) == 0b0110,
             w -> String.format("ldr r%d, r%d, %d", w >> 9 & 0x7, w >> 6 & 0x7, sext(w & 0x3f, 6))),

        LEA (w -> (w >> 12 & 0xf) == 0b1110,
             w -> String.format("lea r%d, %d", w >> 9 & 0x7, sext(w & 0x1ff, 9))),

        NOT (w -> (w >> 12 & 0xf) == 0b1001 && (w & 0x3f) == 0b111111,
             w -> String.format("not r%d, r%d", w >> 9 & 0x7, w >> 6 & 0x7)),

        RET (w -> (w >> 12 & 0xf) == 0b1100 && (w >> 9 & 0x7) == 0 && (w >> 6 & 0x7) == 7 && (w & 0x3f) == 0,
             w -> "ret"),

        RTI (w -> w == 0x8000,
             w -> "rti"),

        ST  (w -> (w >> 12 & 0xf) == 0b0011,
             w -> String.format("st r%d, %d", w >> 9 & 0x7, sext(w & 0x1ff, 9))),

        STI (w -> (w >> 12 & 0xf) == 0b1011,
             w -> String.format("sti r%d, %d", w >> 9 & 0x7, sext(w & 0x1ff, 9))),

        STR (w -> (w >> 12 & 0xf) == 0b0111,
             w -> String.format("str r%d, r%d, %d", w >> 9 & 0x7, w >> 6 & 0x7, sext(w & 0x3f, 6))),

        // These TRAP assembler names need to be above TRAP below so that
        // they'll match before TRAP
        GETC(w -> w == 0xf020,
             w -> "getc"),

        // [Screw] it, I'm saying it. Tom's name for this is better than the textbook's (IN)
        PUTC(w -> w == 0xf021,
             w -> "putc"),

        PUTS(w -> w == 0xf022,
             w -> "puts"),

        IN  (w -> w == 0xf023,
             w -> "in"),

        HALT(w -> w == 0xf025,
             w -> "halt"),

        TRAP(w -> (w >> 12 & 0xf) == 0b1111 && (w >> 8 & 0xf) == 0,
             w -> String.format("trap x%02x", w & 0xff));

        final IntPredicate accepts;
        final Function<Integer, String> asmFormatter;

        Instruction(IntPredicate accepts, Function<Integer, String> asmFormatter) {
            this.accepts = accepts;
            this.asmFormatter = asmFormatter;
        }

        // Sign-extends an integer src into a full-blown 32-bit Java int
        static int sext(int src, int srcbits) {
            return (((src & (1 << (srcbits - 1))) == 0)? 0 : (-1 << srcbits)) | src;
        }

        static Optional<String> decode(int word) {
            return Arrays.stream(Instruction.values())
                         .filter(inst -> inst.accepts.test(word))
                         .findFirst()
                         .map(inst -> inst.asmFormatter.apply(word));
        }

        static String decodeOrFill(int word, boolean hex) {
            return decode(word).orElseGet(() ->
                hex? String.format(".fill x%04x", word)
                   : String.format(".fill %d", sext(word, 16)));
        }
    }
}
