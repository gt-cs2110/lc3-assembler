import java.io.PrintStream;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.lang.NumberFormatException;
import java.util.Collections;

/**
 * Simple proof of concept assembler for LC3
 * obj file does not contain the number of values at every orig, instead orig is preceded by ORIG:
 *      ex: ORIG: x3000
 * some bounds checks for labels, offsets, and immediate values
 * Author: Pulkit Gupta
 */
public class LC3asm {

    // utility class for symbol table:
    static class Symbol {
        // slightly bad practice, but fields are accessed by enclosing class so getters/setters unnecessary
        private int address; // the address that the label points to
        private String label; // the string representation of the label
        private boolean nested = false; // does the symbol reference another symbol?
        private String nestLabel = ""; // what is the label for the symbol being referenced
        private int external = 0; // does this symbol reference another file?
        private boolean extern_symbol = false; // is this from an extern statement?

        public Symbol(int address, String label) {
            this.address = address;
            this.label = label;
        }

        public String toString() {
            return "x" + Integer.toString(address, 16) + "\t: " + label;
        }

        public int hashCode() {
            return label.hashCode();
        }
    }

    static int lc = 0; // Location Counter
    static int pass; // determines which pass the assembler is on
    static Scanner read; // scanner for the input file
    static PrintStream obj; // printstream for object file
    static PrintStream sym; // printstream for symboltable
    static PrintStream debug; // printstream for debug
    static PrintStream dat; // printstream for dat file (for datapath)
    static Map<String, Symbol> symbolTable = new HashMap<>(); // runtime copy of symbol table
    static String[] pseudoOps = {".ORIG", ".END", ".FILL", ".BLKW", ".STRINGZ", ".EXTERNAL"}; // array of pseudoOps
    static List<String> directives = Arrays.asList(pseudoOps); // list of all pseudoOps
    static String[] instructions = {"ADD", "AND", "BR", "JMP", "JSR", "JSRR", "LD", "LDI", "LDR", "LEA", "NOT", "RET", "ST", "STI", "STR", "TRAP", "GETC", "OUT", "PUTS", "IN", "HALT"};
    static List<String> mnemonics = Arrays.asList(instructions); // list of all instructions and Trap Aliases
    static boolean done = false; // detect if missing end statements

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("error incorrect inputs, usage: java LC3asm <local path to input file>");
        }
        try {

            System.out.println("if \"Success!!\" isnt printed, assembly process has failed, check debug file");

            //here we are creating the output files and PrintStreams for them, this allows us to use them like System.out
            String filebase = args[0].substring(0, args[0].lastIndexOf('.')); // get the root filename without any extensions
            File outfile = new File(filebase + ".obj"); //initialize an output file for .obj
            obj = new PrintStream(outfile); // create printstream for the object file
            File symbolfile = new File(filebase + ".sym"); // output file for .sym
            sym = new PrintStream(symbolfile); // printstream for symbol table
            File debugfile = new File(filebase + ".debug"); // output file for debug information
            debug = new PrintStream(debugfile); // printstream for debug file
            File datfile = new File(filebase + ".dat"); // output file for use in datapath
            dat = new PrintStream(datfile); // printstream for debug file

            read = new Scanner(new File(args[0])); //initialize scanner for first pass
            pass = 1; // set pass to 1
            parse(); // run pass 1

            //add nested symbols
            boolean nestResolved = false;
            while (!nestResolved) { // loop until deep references are resolved
                nestResolved = true;
                for (Symbol s : symbolTable.values()) { //search the symbol table and update the value in the corresponding entry
                    if (s.nested) {
                        if (symbolTable.containsKey(s.nestLabel)) {
                            s.nested = false;
                            if (symbolTable.get(s.nestLabel).extern_symbol) {
                                s.external = 1;
                            }
                        } else {
                            nestResolved = false;
                        }
                    }
                }
            }

            List<Symbol> symbols = new ArrayList<>(symbolTable.values());
            Collections.sort(symbols, (a, b) -> { // sort the symbol table by address for readability in print form
                if (a.address >= b.address) {
                    return 1;
                } else {
                    return -1;
                }
            });

            sym.println( "ADDRESS            LABEL            EXTERNAL     EXTLABEL");
            String fmt = "x%04x              %-10s       %1d            %s\n"; // a string format for printing the individual symbols
            for (Symbol s : symbols) {
                if (s.extern_symbol) { // Don't print extern statements
                    continue;
                }
                sym.printf(fmt, s.address, s.label, s.external, s.nestLabel);
            }
            System.out.println("Pass 1 complete, symbol table at: " + filebase + ".sym");

            read.close();
            read = null;
            read = new Scanner(new File(args[0])); //reset scanner
            lc = 0; // reset lc
            pass = 2; // set pass to 2
            parse(); // run pass 2

            debug.println("Success!!");
            System.out.println("Success!!");

        } catch (FileNotFoundException fnf) {
            fnf.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * prints to object file and to dat file
     * @param outstring: the string to be output
    */
    private static void output(String outstring) {
        obj.println(outstring);
        dat.println(outstring.substring(outstring.indexOf("x") + 1)); // print without leading 'x'
    }

    /**
     * runs each pass of the assembler
     * parses the input and disregards comments
     * processes each line to generate the symbol table and the instructions as necessary
     *
     */
    private static void parse() {
        while (read.hasNext()) {

            String input = read.nextLine().trim(); // read the next line
            if (input.contains(";")){
                input = input.substring(0, input.indexOf(";")); // truncate any comments off the end
            }
            //System.out.println(Integer.toString(lc, 16) + ": " + input); // if you need to see which address a line of assembly maps to, uncomment this
            if (input.length() == 0) continue;

            String[] sepStrLit = new String[2]; // need to separate out any string literals in the line
            if (input.contains("\"")) {
                int idx = input.indexOf("\"");
                sepStrLit[0] = input.substring(0, idx).toUpperCase(); // uppercase to ease parsing
                sepStrLit[1] = input.substring(idx+1, input.length()-1); // remainder of line except for quotes
                if (input.charAt(input.length() - 1) != '\"') { // check for closing '\"' in string literal
                    debug.println("invalid string literal in line");
                    System.exit(1);
                }
            } else {
                sepStrLit[0] = input.toUpperCase(); // uppercase to ease parsing
                sepStrLit[1] = "";
            }
            String[] instWords = sepStrLit[0].split("[, \t]+");
            List<String> words = new ArrayList<String>(Arrays.asList(instWords)); // split the string on spaces and commas
            if (sepStrLit[1].length() != 0) words.add(words.size(), sepStrLit[1]);

            // if first word is not a pseudo op or opcode mnemonic, it must be a label
            // only supports labels with 2 or more characters
            // labels must not start with "BR"
            if (!directives.contains(words.get(0)) && !mnemonics.contains(words.get(0)) && !words.get(0).substring(0,2).equals("BR")) {
                //on pass 1 generate the symbol table
                if (pass == 1) {
                    gen_label(words);
                } else { // on pass 2 dont need to recreate label
                    words.remove(0);
                }
            }

            if (words.size() == 0) continue; // if a label is on a line by itself, there is nothing else to parse

            //process directives
            if (directives.contains(words.get(0))) {
                //label corresponds to a pseudoOP
                //{".ORIG", ".END", ".FILL", ".BLKW", ".STRINGZ"}
                switch (directives.indexOf(words.get(0))) {
                    case 0: // orig
                        gen_orig(words);
                        break;
                    case 1: // end
                        gen_end(words);
                        break;
                    case 2: // fill
                        gen_fill(words);
                        break;
                    case 3: // blkw
                        gen_blkw(words);
                        break;
                    case 4: // stringz
                        gen_stringz(words);
                        break;
                    case 5: // external
                        gen_external(words);
                        break;
                    default:
                        debug.println("invalid directive");
                        System.exit(1);
                        break;
                }
            }

            //process instructions and aliases
            if (mnemonics.contains(words.get(0)) || words.get(0).substring(0,2).equals("BR")) {
                if (words.get(0).substring(0,2).equals("BR")) {
                    //need to catch BR statements here as the conditioncode is part of the opcode mnemonic
                    gen_br(words);
                } else {
                //{"ADD", "AND", "BR", "JMP", "JSR", "JSRR", "LD", "LDI", "LDR", "LEA", "NOT", "RET", "ST", "STI", "STR", "TRAP", "HALT"}
                    switch (mnemonics.indexOf(words.get(0))) {
                        case 0: // ADD
                            gen_add(words);
                            break;
                        case 1: // AND
                            gen_and(words);
                            break;
                        case 2: // BR, this is caught earlier because of nzp being part of the mnemonic
                            //gen_br(words);
                            break;
                        case 3: // JMP
                            gen_jmp(words);
                            break;
                        case 4: // JSR
                            gen_jsr(words);
                            break;
                        case 5: // JSRR
                            gen_jsrr(words);
                            break;
                        case 6: // LD
                            gen_ld(words);
                            break;
                        case 7: // LDI
                            gen_ldi(words);
                            break;
                        case 8: // LDR
                            gen_ldr(words);
                            break;
                        case 9: // LEA
                            gen_lea(words);
                            break;
                        case 10: // NOT
                            gen_not(words);
                            break;
                        case 11: // RET
                            gen_ret(words);
                            break;
                        case 12: // ST
                            gen_st(words);
                            break;
                        case 13: // STI
                            gen_sti(words);
                            break;
                        case 14: // STR
                            gen_str(words);
                            break;
                        case 15: // TRAP
                            gen_trap(words);
                            break;
                        case 16: //GETC
                            gen_getc(words);
                            break;
                        case 17: //OUT
                            gen_out(words);
                            break;
                        case 18: //PUTS
                            gen_puts(words);
                            break;
                        case 19: //IN
                            gen_in(words);
                            break;
                        case 20: //HALT
                            gen_halt(words);
                            break;
                        default:
                            debug.println(words);
                            debug.println("invalid mnemonic");
                            System.exit(1);
                            break;
                    }
                }
            }
        }

        if (!done) {
            debug.println("missing .end");
            System.exit(1);
        }
        done = false;
    }

    /**
     * Takes in a string with an optional leading x or # followed by a numeric value,
     * determines the appropriate base/radix (10 or 16),
     * and returns the integer version of this number
     *
     * @param num: the string containing a number
     * @return the integer value represented by num
     */
    private static int str2int(String num) {
        int radix = 10;
        if (num.charAt(0) == 'X') {
            radix = 16;
            num = num.substring(1);
        } else if (num.charAt(0) == '#') {
            num = num.substring(1);
        }
        int value = 1;
        if (num.charAt(0) == '-') { // set the sign of the value
            value = -1;
            num = num.substring(1);
        }
        if ((radix == 16 && num.length() > 4) || (radix == 10 && num.length() > 5)) { // determine if input valid;
            int first = ((int) num.charAt(0)) - '0';
            char fchar = num.toUpperCase().charAt(0);
            if (!((first >= 0 && first <= 9) || (fchar >= 'A' && fchar <= 'F' && radix == 16))) { // input not a valid integer literal
                throw(new NumberFormatException()); //not a valid number
            } else {
                debug.println("error: immediate value too large: " + num + " at LC: " + int2hex(lc));
                System.exit(1);
            }
        }
        try {
            value *= Integer.parseInt(num, radix); // multiply to include sign
        } catch (NumberFormatException nfe) {
            //nfe.printStackTrace();
            throw(nfe);
        }
        return value;

    }

    /**
     * Takes in an integer and returns it as a 4 character hexadecimal string
     *
     * Note that this method will only consider the least significant sixteen
     * bits. Anything higher will be discarded. This should usually work fine,
     * but can lead to subtle issues.
     *
     * @param num: the integer for which a hex string is required
     * @return a hexadecimal string containing a leading x followed by the value num
     */
    private static String int2hex(int num) {
        // Isolate the least significant sixteen bits and print them
        return String.format("x%04x", num & 0xFFFF);
    }

    /**
     * Takes in an offset and the width of the encoded offset
     * 
     * Determines whether the offset fits within the 2'c complement
     * range for the given bitwidth
     * 
     * @param offset: the offset provided by the label or programmer
     * @param width: the width of the offset as defined by the LC-3 ISA
     * @return true if the offset fits within the 2's complement range, false otherwise
     */
    private static boolean validate_2c_offset(int offset, int width) {
        int minimum = -1 * (1 << (width - 1));
        int maximum = (1 << (width - 1)) - 1;
        if (offset > maximum || offset < minimum) {
            debug.println("invalid offset: " + offset + " for bit width " + width);
            return false;
        } else {
            return true;
        }
    }

    /**
     * generates a symbol table entry for the label
     */
    private static void gen_label(List<String> words) {
        String lbl = words.get(0); // extract the label

        words.remove(0);
        if (words.size() >= 1 && words.get(0).equals(".ORIG")) {
            gen_orig(words);
        }

        Symbol label = new Symbol(lc, lbl);
        debug.println("created symbol: " + label);
        symbolTable.put(lbl, label);
    }

    /**
     * updates the lc according to the .orig statement
     */
    private static void gen_orig(List<String> words) {
        int oldLC = lc;
        lc = str2int(words.get(1));
        done = false;
        if (pass == 2) {
            obj.println("ORIG: " + int2hex(lc));
            int numZero = lc - oldLC;
            for (int i = 0; i < numZero; i++) { // print 0's in dat file
                dat.println("0000");
            }
        }
    }

    /**
     * marks the end of the file
     */
    private static void gen_end(List<String> words) {
        done = true;
        //this code below is for only 1 .orig/.end statement as the book defines it
        // if (!done) {
        //     done = true;
        // } else {
        //     debug.println("can only have one .end statement");
        //     System.exit(1);
        // }
    }

    /**
     * updates a symbol in the symbol table if it is there
     * creates the entry in obj file
     */
    private static void gen_fill(List<String> words) {
        boolean nested = false;
        int value = 999999999; // default value too large for 16 bits
        try {
            value = str2int(words.get(1)); // extract value from assembly code
        } catch (NumberFormatException nfe) {
            nested = true; // parameter for fill is actually a label
        }
        if (pass == 1) {
            if (nested) {
                for (Symbol s : symbolTable.values()) { //search the symbol table and update the nested characteristics
                    if (s.address == lc) {
                        s.nested = true;
                        s.nestLabel = words.get(1);
                    }
                }
            }
        } else if (pass == 2) {
            if (nested) {
                output(int2hex(symbolTable.get(words.get(1)).address));
            } else {
                output(int2hex(value)); // add the hex value to the object file
            }
        }

        lc++; // increment lc
    }

    /**
     * updates a symbol in the symbol table to be external
     */
    private static void gen_external(List<String> words) {
        if (pass == 1) {
            Symbol external = new Symbol(-1, words.get(1));
            external.extern_symbol = true;
            symbolTable.put(words.get(1), external);
        }
    }

    /**
     * increments lc by the correct amount and makes room in the obj file
     */
    private static void gen_blkw(List<String> words) {
        int w = str2int(words.get(1)); // get the size of block
        lc += (w - 1); // make room for the word block (-1 because first word at initial lc)
        if (pass == 2) {
            for (int i = 0; i < w; i++) {
                output(int2hex(0));
            }
        }
        lc++; // increment lc after processing asm directive
    }

    /**
     * updates lc and populates obj file
     */
    private static void gen_stringz(List<String> words) {
        String s = words.get(1); //the string to be placed in memory at LC
        int len = s.length();
        if (pass == 2) {
            for (int i = 0; i < len; i++) {
                int c = (int) s.charAt(i);
                if (s.charAt(i) == '\\') { // catch escape characters
                    if (s.charAt(i+1) == 'n') {
                        output(int2hex(0x20)); // unfortunately due to the way len is implemented, will need to replace the '\' char with a space
                        output(int2hex(0x0D));
                        i++;
                    } else {
                        output(int2hex(c)); //output a slash if unimplemented escape character
                    }
                } else{
                    output(int2hex(c)); // place char in memory
                }
            }
            output(int2hex(0)); // null terminator (the z in stringZ)
        }
        lc += (len - 1); // increment lc by size of string (-1 because first char is at initial lc)
        lc += 1; // add the null terminator
        lc++; // increment lc after processing an assembler directive
    }

    /**
     * generates the binary encoding of the ADD instruction
     */
    private static void gen_add(List<String> words) {
        if (pass == 2) {
            if (words.size() != 4) {
                debug.println("invalid add instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 1; //0001
            int dr = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (dr < 0 || dr > 7) {
                debug.println("invalid destination register: " + words);
                System.exit(1);
            }
            int sr1 = str2int(words.get(2).substring(1)); // truncate the leading R from the register specification
            if (sr1 < 0 || sr1 > 7) {
                debug.println("invalid SR1: " + words);
                System.exit(1);
            }
            int imm = 0;
            int val = 0;
            if (words.get(3).charAt(0) == 'R') {
                imm = 0;
                val = str2int(words.get(3).substring(1)); // truncate R for register specifier, forces base 10
                if (val < 0 || val > 7) {
                    debug.println("invalid SR2: " + words);
                    System.exit(1);
                }
            } else {
                imm = 1;
                val = str2int(words.get(3)); //get the imm5
                if (!validate_2c_offset(val, 5)) {
                    System.exit(-1);
                }
            }
            val = val & 0x001F; // only keep the lower 5 bits of the imm;
            instruction = opcode << 12 | dr << 9 | sr1 << 6 | imm << 5 | val;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the AND instruction
     */
    private static void gen_and(List<String> words) {
        if (pass == 2) {
            if (words.size() != 4) {
                debug.println("invalid and instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 5; // 0101
            int dr = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (dr < 0 || dr > 7) {
                debug.println("invalid destination register: " + words);
                System.exit(1);
            }
            int sr1 = str2int(words.get(2).substring(1)); // truncate the leading R from the register specification
            if (sr1 < 0 || sr1 > 7) {
                debug.println("invalid SR1: " + words);
                System.exit(1);
            }
            int imm = 0;
            int val = 0;
            if (words.get(3).charAt(0) == 'R') {
                imm = 0;
                val = str2int(words.get(3).substring(1)); // truncate R for register specifier, forces base 10
                if (val < 0 || val > 7) {
                    debug.println("invalid SR2: " + words);
                    System.exit(1);
                }
            } else {
                imm = 1;
                val = str2int(words.get(3)); //get the imm5
                if (!validate_2c_offset(val, 5)) {
                    System.exit(-1);
                }
            }
            val = val & 0x001F; // only keep the lower 5 bits of the imm;
            instruction = opcode << 12 | dr << 9 | sr1 << 6 | imm << 5 | val;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the BR instruction
     */
    private static void gen_br(List<String> words) {
        if (pass == 2) {
            if (words.size() != 2) {
                debug.println("invalid BR instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 0; // 0000

            int n = 0, z = 0, p = 0;
            String code = words.get(0).substring(2); // grab everything after the BR

            if (code.contains("N")) n = 1;
            if (code.contains("Z")) z = 1;
            if (code.contains("P")) p = 1;
            if (code.length() == 0) {
                n = 1;
                z = 1;
                p = 1;
            }

            String lbl = words.get(1);
            Symbol s = symbolTable.get(lbl);
            int offset = s.address - (lc + 1); //PCOffset is from lc+1
            if (!validate_2c_offset(offset, 9)) {
                System.exit(1);
            }
            offset = offset & 0x01FF; // keep only the lower 9 bits

            instruction = opcode << 12 | n << 11 | z << 10 | p << 9 | offset;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the JMP instruction
     */
    private static void gen_jmp(List<String> words) {
        if (pass == 2) {
            if (words.size() != 2) {
                debug.println("invalid jmp instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 12; //1100
            int baseR = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (baseR < 0 || baseR > 7) {
                debug.println("invalid base register: " + words);
                System.exit(1);
            }
            instruction = opcode << 12 | baseR << 6;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the JSR instruction
     */
    private static void gen_jsr(List<String> words) {
        if (pass == 2) {
            if (words.size() != 2) {
                debug.println("invalid JSR instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 4; // 0100

            String lbl = words.get(1);
            Symbol s = symbolTable.get(lbl);
            int offset = s.address - (lc + 1); //PCOffset is from lc+1
            if (!validate_2c_offset(offset, 11)) {
                System.exit(1);
            }
            offset = offset & 0x07FF; // keep only the lower 11 bits

            instruction = opcode << 12 | 1 << 11 | offset;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the JSRR instruction
     */
    private static void gen_jsrr(List<String> words) {
        if (pass == 2) {
            if (words.size() != 2) {
                debug.println("invalid jsrr instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 4; // 0100
            int baseR = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (baseR < 0 || baseR > 7) {
                debug.println("invalid base register: " + words);
                System.exit(1);
            }
            instruction = opcode << 12 | baseR << 6;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the LD instruction
     */
    private static void gen_ld(List<String> words) {
        if (pass == 2) {
            if (words.size() != 3) {
                debug.println("invalid ld instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 2; // 0010
            int dr = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (dr < 0 || dr > 7) {
                debug.println("invalid destination register: " + words);
                System.exit(1);
            }
            String lbl = words.get(2);
            Symbol s = symbolTable.get(lbl);
            int offset = s.address - (lc + 1);
            if (!validate_2c_offset(offset, 11)) {
                System.exit(1);
            }
            offset = offset & 0x01FF; // keep lower 9 bits
            instruction = opcode << 12 | dr << 9 | offset;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the LDI instruction
     */
    private static void gen_ldi(List<String> words) {
        if (pass == 2) {
            if (words.size() != 3) {
                debug.println("invalid ldi instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 10; // 1010
            int dr = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (dr < 0 || dr > 7) {
                debug.println("invalid destination register: " + words);
                System.exit(1);
            }
            String lbl = words.get(2);
            Symbol s = symbolTable.get(lbl);
            int offset = s.address - (lc + 1);
            if (!validate_2c_offset(offset, 9)) {
                System.exit(1);
            }
            offset = offset & 0x01FF; // keep lower 9 bits
            instruction = opcode << 12 | dr << 9 | offset;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the LDR instruction
     */
    private static void gen_ldr(List<String> words) {
        if (pass == 2) {
            if (words.size() != 4) {
                debug.println("invalid ldr instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 6; // 0110
            int dr = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (dr < 0 || dr > 7) {
                debug.println("invalid destination register: " + words);
                System.exit(1);
            }
            int baseR = str2int(words.get(2).substring(1)); // truncate the leading R from the register specification
            if (baseR < 0 || baseR > 7) {
                debug.println("invalid baseR: " + words);
                System.exit(1);
            }
            int offset = str2int(words.get(3)); //get the offset6
            if (!validate_2c_offset(offset, 6)) {
                System.exit(1);
            }
            offset = offset & 0x003F; // only keep the lower 6 bits of the offset;
            instruction = opcode << 12 | dr << 9 | baseR << 6 | offset;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the LEA instruction
     */
    private static void gen_lea(List<String> words) {
        if (pass == 2) {
            if (words.size() != 3) {
                debug.println("invalid lea instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 14; // 1110
            int dr = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (dr < 0 || dr > 7) {
                debug.println("invalid destination register: " + words);
                System.exit(1);
            }
            String lbl = words.get(2);
            Symbol s = symbolTable.get(lbl);
            int offset = s.address - (lc + 1);
            if (!validate_2c_offset(offset, 9)) {
                System.exit(1);
            }
            offset = offset & 0x01FF; // keep lower 9 bits
            instruction = opcode << 12 | dr << 9 | offset;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the NOT instruction
     */
    private static void gen_not(List<String> words) {
        if (pass == 2) {
            if (words.size() != 3) {
                debug.println("invalid not instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 9; // 1001
            int dr = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (dr < 0 || dr > 7) {
                debug.println("invalid destination register: " + words);
                System.exit(1);
            }
            int sr = str2int(words.get(2).substring(1)); // truncate the leading R from the register specification
            if (sr < 0 || sr > 7) {
                debug.println("invalid SR1: " + words);
                System.exit(1);
            }
            int fill = (-1) & 0x003F; // need the 6 ones at the end of the instruction
            instruction = opcode << 12 | dr << 9 | sr << 6 | fill;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the RET alias
     */
    private static void gen_ret(List<String> words) {
        words.add(1, "R7");
        gen_jmp(words);
    }

    /**
     * generates the binary encoding of the ST instruction
     */
    private static void gen_st(List<String> words) {
        if (pass == 2) {
            if (words.size() != 3) {
                debug.println("invalid st instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 3; // 0011
            int sr = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (sr < 0 || sr > 7) {
                debug.println("invalid source register: " + words);
                System.exit(1);
            }
            String lbl = words.get(2);
            Symbol s = symbolTable.get(lbl);
            int offset = s.address - (lc + 1);
            if (!validate_2c_offset(offset, 9)) {
                System.exit(1);
            }
            offset = offset & 0x01FF; // keep lower 9 bits
            instruction = opcode << 12 | sr << 9 | offset;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the STI instruction
     */
    private static void gen_sti(List<String> words) {
        if (pass == 2) {
            if (words.size() != 3) {
                debug.println("invalid sti instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 11; // 1011
            int sr = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (sr < 0 || sr > 7) {
                debug.println("invalid source register: " + words);
                System.exit(1);
            }
            String lbl = words.get(2);
            Symbol s = symbolTable.get(lbl);
            int offset = s.address - (lc + 1);
            if (!validate_2c_offset(offset, 9)) {
                System.exit(1);
            }
            offset = offset & 0x01FF; // keep lower 9 bits
            instruction = opcode << 12 | sr << 9 | offset;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the STR instruction
     */
    private static void gen_str(List<String> words) {
        if (pass == 2) {
            if (words.size() != 4) {
                debug.println("invalid ldr instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 7; // 0111
            int sr = str2int(words.get(1).substring(1)); // truncate the leading R from the register specification
            if (sr < 0 || sr > 7) {
                debug.println("invalid source register: " + words);
                System.exit(1);
            }
            int baseR = str2int(words.get(2).substring(1)); // truncate the leading R from the register specification
            if (baseR < 0 || baseR > 7) {
                debug.println("invalid baseR: " + words);
                System.exit(1);
            }
            int offset = str2int(words.get(3)); //get the offset6
            if (!validate_2c_offset(offset, 6)) {
                System.exit(1);
            }
            offset = offset & 0x003F; // only keep the lower 6 bits of the offset;
            instruction = opcode << 12 | sr << 9 | baseR << 6 | offset;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the TRAP instruction
     */
    private static void gen_trap(List<String> words) {
        if (pass == 2) {
            if (words.size() != 2) {
                debug.println("invalid trap instruction: " + words);
                System.exit(1);
            }
            int instruction = 0;
            int opcode = 15; // 1111
            int trapvect8 = str2int(words.get(1));
            trapvect8 = trapvect8 & 0x00FF; // keep the 8 bits for the trap vect
            instruction = opcode << 12 | trapvect8;
            output(int2hex(instruction));
        }
        lc++;
    }

    /**
     * generates the binary encoding of the GETC alias
     */
    private static void gen_getc(List<String> words) {
        words.add(1, "X20");
        gen_trap(words);
    }

    /**
     * generates the binary encoding of the OUT alias
     */
    private static void gen_out(List<String> words) {
        words.add(1, "X21");
        gen_trap(words);
    }

    /**
     * generates the binary encoding of the PUTS alias
     */
    private static void gen_puts(List<String> words) {
        words.add(1, "X22");
        gen_trap(words);
    }

    /**
     * generates the binary encoding of the IN alias
     */
    private static void gen_in(List<String> words) {
        words.add(1, "X23");
        gen_trap(words);
    }

    /**
     * generates the binary encoding of the HALT alias
     */
    private static void gen_halt(List<String> words) {
        words.add(1, "X25");
        gen_trap(words);
    }

}