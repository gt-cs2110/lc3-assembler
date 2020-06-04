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
 * no support for trap aliases (In, Out, Puts, etc)
 * obj file does not contain the number of values at every orig, instead orig is preceded by ORIG:
 *      ex: ORIG: x3000
 * no bounds checks for labels or any immediate values
 * Author: Pulkit Gupta
 */
public class LC3asm {

    // utility class for symbol table:
    static class Symbol {
        private int address; // the address that the label points to
        private String label; // the string representation of the label
        private int value; // the value, should one be necessary

        public Symbol(int address, String label, int value) {
            this.address = address;
            this.label = label;
            this.value = value;
        }

        public String toString() {
            return "x" + Integer.toString(address, 16) + "\t: " + label + "\t: " + Integer.toString(value, 16);
        }
    }

    static int lc = 0; // Location Counter
    static int pass; // determines which pass the assembler is on
    static Scanner read; // scanner for the input file
    static PrintStream obj; // printstream for object file
    static PrintStream sym; // printstream for symboltable
    static PrintStream debug; // printstream for debug
    static PrintStream dat;
    static Map<String, Symbol> symbolTable = new HashMap<>(); // runtime copy of symbol table
    static String[] pseudoOps = {".ORIG", ".END", ".FILL", ".BLKW", ".STRINGZ"};
    static List<String> directives = Arrays.asList(pseudoOps); // list of all pseudoOps
    static String[] instructions = {"ADD", "AND", "BR", "JMP", "JSR", "JSRR", "LD", "LDI", "LDR", "LEA", "NOT", "RET", "ST", "STI", "STR", "TRAP", "HALT"};
    static List<String> mnemonics = Arrays.asList(instructions); // list of all instructions and aliases
    static boolean done = false;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("error incorrect inputs, usage: java LC3asm <local path to input file>");
        }
        try {

            System.out.println("if \"Success!!\" isnt printed, failed, check debug");

            //here we are creating the output files and PrintStreams for them, this allows us to use them like System.out
            String filebase = args[0].substring(0, args[0].lastIndexOf('.')); // get the root filename without any extensions
            File outfile = new File(filebase + ".obj"); //initialize an output file for .obj
            obj = new PrintStream(outfile); // create printstream for the object file
            File symbolfile = new File(filebase + ".sym"); // output file for .sym
            sym = new PrintStream(symbolfile); // printstream for symbol table
            File debugfile = new File(filebase + ".debug");
            debug = new PrintStream(debugfile);
            File datfile = new File(filebase + ".dat");
            dat = new PrintStream(datfile);

            read = new Scanner(new File(args[0])); //initialize scanner for first pass
            pass = 1; // set pass to 1
            parse(); // run pass 1

            List<Symbol> symbols = new ArrayList<>(symbolTable.values());
            Collections.sort(symbols, (a, b) -> {
                if (a.address >= b.address) {
                    return 1;
                } else {
                    return -1;
                }
            });

            sym.println("ADDRESS\t\t\tLABEL\t\tVALUE");
            String fmt = "x%04x\t\t%10s\t\t%04x\n"; // a string format for printing the individual symbols
            for (Symbol s : symbols) {
                sym.printf(fmt, s.address, s.label, s.value);
            }
            System.out.println("Pass 1 complete, symbol table at: " + filebase + ".sym");

            read.close();
            read = null;
            read = new Scanner(new File(args[0])); //reset scanner
            pass = 2; // set pass to 2
            parse(); // run pass 2

            debug.println("Success!!");
            System.out.println("Success!!");

        } catch (FileNotFoundException fnf) {
            fnf.printStackTrace();
            System.exit(1);
        }

    }

    private static void output(String outstring) {
        obj.println(outstring);
        dat.println(outstring.substring(outstring.indexOf("x") + 1));
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
            input = input.toUpperCase(); // make it uppercase so parsing is easier
            //System.out.println(Integer.toString(lc, 16) + ": " + input); // if you need to see which address a line of assembly maps to, uncomment this
            if (input.length() == 0) continue;
            List<String> words = new ArrayList<String>(Arrays.asList(input.split("[, \t]+"))); // split the string on spaces and commas

            // if first word is not a pseudo op or opcode mnemonic, it must be a label
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
                        case 16: //HALT
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
        if (num.length() > 4) {
            debug.println("error: immedate value too large: " + num + " at LC: " + int2hex(lc));
            System.exit(1);

        }
        int value = 0;
        try {
            value = Integer.parseInt(num, radix);
        } catch (NumberFormatException nfe) {
            nfe.printStackTrace();
            System.exit(1);
        }
        return value;

    }

    /**
     * takes in an integer and returns it as a 4 character hexadecimal string
     *
     * @param num: the integer for which a hex string is required
     * @return a hexadecimal string containing a leading x followed by the value num
     */
    private static String int2hex(int num) {
        return String.format("x%04x", num);
    }

    /**
     * generates a symbol table entry for the label
     */
    private static void gen_label(List<String> words) {
        String lbl = words.get(0); // extract the label

        words.remove(0);
        int value = 0; // value will be updated by the other
        if (words.size() >= 1 && words.get(0).equals(".ORIG")) {
            gen_orig(words);
        }

        Symbol label = new Symbol(lc, lbl, value);
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
            int numZero = lc - oldLC + 1;
            for (int i = 0; i < numZero; i++) {
                dat.println("0000");
            }
        }
    }

    /**
     * marks the end of the file
     */
	private static void gen_end(List<String> words) {
        lc++;
        done = true;
        //this code below is for only 1 .end statement as the book defines it
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
        int value = str2int(words.get(1)); // extract value from assembly code
        if (pass == 1) {
            for (Symbol s : symbolTable.values()) { //search the symbol table and update the value in the corresponding entry
                if (s.address == lc) {
                    s.value = value;
                }
            }
        } else if (pass == 2) {
            output(int2hex(value)); // add the hex value to the object file
        }
        lc++; // increment lc

    }

    /**
     * increments lc by the correct amount and makes room in the obj file
     */
	private static void gen_blkw(List<String> words) {
        int w = str2int(words.get(1)); // get the size of block
        lc+=w; // make room for the word block
        if (pass == 2) {
            for (int i = 0; i < w; i++) {
                output(int2hex(0));
            }
        }
        //lc++; // increment lc after processing asm directive
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
                output(int2hex(c)); // place char in memory
            }
            output(int2hex(0)); // null terminator (the z in stringZ)
        }
        lc += len; // increment lc by size of string
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
     * generates the binary encoding of the HALT alias
     */
    private static void gen_halt(List<String> words) {
        words.add(1, "X25");
        gen_trap(words);
	}

}