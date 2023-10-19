import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.io.File;
import java.io.PrintStream;

/**
 * Proof of concept linker for the LC-3
 * takes in basenames for the files to be linked together
 * aggregates their symbol tables
 * then reads in each object file and repairs any external values
 */
public class LC3link {

    static class Symbol {
        private int address; // the address that the label points to
        private String label; // the string representation of the label
        private List<Integer> fillAddresses; // the address of this label needs to be written to these addresses

        public Symbol(int address, String label) {
            this.address = address;
            this.label = label;
            this.fillAddresses = new ArrayList<Integer>();
        }

        public String toString() {
            return "x" + Integer.toString(address, 16) + "\t: " + label;
        }

        public int hashcode() {
            return label.hashCode();
        }
    }

    static Map<String, Symbol> symbolTable = new HashMap<>(); // runtime copy of symbol table
    
    static Map<Integer, Integer> repairLocations = new HashMap<>();

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("error incorrect inputs. Usage:");
            System.out.println("java Lc3Link <objfile1> <objfile2> ... <objfile3>");
            System.exit(1);
        }

        // go through all the filenames and build the aggregate symbol table
        for (String filename : args) {
            String filebase = filename.substring(0, filename.lastIndexOf('.')); // get the root filename without any extensions
            String symbol_filename = filebase + ".sym";
            File symfile = new File(symbol_filename);
            Scanner symreader = new Scanner(symfile);
            symreader.nextLine(); // skip the header line
            while (symreader.hasNext()) {
                String[] words = symreader.nextLine().trim().split(" +");
                String addressStr = words[0];
                String label = words[1];
                String externalStr = words[2];

                int address = Integer.parseInt(addressStr.substring(1), 16);
                boolean external = Integer.parseInt(externalStr) == 1;

                if (!external) {
                    if (symbolTable.containsKey(label)) {
                        Symbol sym = symbolTable.get(label);
                        if (sym.address < 0) {
                            // no problem, another file using this label
                            // .EXTERNALly already added a dummy entry to the
                            // symbol table. fix the address now
                            sym.address = address;
                        } else {
                            System.err.println("Symbol " + label + " defined multiple times");
                            System.exit(1);
                        }
                    } else {
                        symbolTable.put(label, new Symbol(address, label));
                    }
                } else { // !!external
                    Symbol sym;
                    if (symbolTable.containsKey(label)) {
                        sym = symbolTable.get(label);
                    } else {
                        sym = new Symbol(-1, label);
                        symbolTable.put(label, sym);
                    }
                    sym.fillAddresses.add(address);
                }
            }
            symreader.close();
        }

        for (Symbol sym : symbolTable.values()) {
            if (sym.address < 0) {
                System.err.println("Undefined symbol " + sym.label);
                System.exit(1);
            }

            for (int fillAddress : sym.fillAddresses) {
                repairLocations.put(fillAddress, sym.address);
            }
        }

        PrintStream symbols_out = new PrintStream(new File("output.sym"));
        String symbol_fmt = "x%04x              %-10s       %1d\n";

        symbols_out.println( "ADDRESS            LABEL            EXTERNAL");
        for (Symbol s : symbolTable.values()) {
            symbols_out.printf(symbol_fmt, s.address, s.label, 0);
        }

        // open all the input files and as you go through them, calculate LC
        // when the LC for a repair location is encountered, use the symbol instead of the value in text

        PrintStream obj_out = new PrintStream(new File("output.obj"));
        for (String filename : args) {
            String obj_filename = filename;
            File objfile = new File(obj_filename);
            Scanner objreader = new Scanner(objfile);
            int lc = 0;
            while (objreader.hasNext()) {
                String input = objreader.nextLine().trim().replaceAll(" +", " ");
                String[] words = input.split(" ");
                if (words[0].startsWith("ORIG:")) {
                    lc = Integer.parseInt(words[1].substring(1), 16);
                    obj_out.printf("ORIG: x%04x\n", lc);
                } else {
                    if (repairLocations.containsKey(lc)) {
                        obj_out.printf("x%04x\n", repairLocations.get(lc));
                    } else {
                        obj_out.println(input);
                    }

                    lc++;
                }
            }
            objreader.close();
        }

        // concatenate all of the listings from austin's changes for the lc3tools object file converter

        PrintStream dbgsym_out = new PrintStream(new File("output.dbgsym"));
        for (String filename : args) {
            String filebase = filename.substring(0, filename.lastIndexOf('.')); // get the root filename without any extensions
            String dbgsym_filename = filebase + ".dbgsym";
            File dbgsymfile = new File(dbgsym_filename);
            Scanner dbgsymreader = new Scanner(dbgsymfile);
            while (dbgsymreader.hasNext()) {
                String input = dbgsymreader.nextLine();
                dbgsym_out.println(input);
            }
            dbgsymreader.close();
        }

        System.out.println("Linked object file written to output.obj");
    }
}
