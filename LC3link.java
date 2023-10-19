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
        private String externalLabel = ""; // what is the label for the symbol being referenced
        private int external = 0; // does this symbol reference another file?
        private int fill_value = 0;

        public Symbol(int address, String label) {
            this.address = address;
            this.label = label;
        }

        public String toString() {
            return "x" + Integer.toString(address, 16) + "\t: " + label;
        }

        public int hashcode() {
            return label.hashCode();
        }
    }

    static Map<String, Symbol> symbolTable = new HashMap<>(); // runtime copy of symbol table
    static List<String> extern_users = new ArrayList<>(); // list of labels that need to be filled with an extern
    
    static Map<Integer, Symbol> repairLocations = new HashMap<>();

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
                String input = symreader.nextLine().trim().replaceAll(" +", " ");
                String[] words = input.split(" ");
                int address = Integer.parseInt(words[0].substring(1), 16);
                Symbol s = new Symbol(address, words[1]);
                symbolTable.put(s.label, s);
                if (words.length == 4) {
                    // this is a symbol that needs an external fill
                    s.external = 1;
                    s.externalLabel = words[3];
                    extern_users.add(words[1]);
                }
            }
            symreader.close();
        }

        System.out.println("Extern Users: " + extern_users.toString());

        System.out.println("resolving extern in symbol table");
        
        String printfmt = "x%04x              %-10s       %1d            %-10s          x%04x\n";
        for (String user : extern_users) {
            Symbol s = symbolTable.get(user);
            s.fill_value = symbolTable.get(s.externalLabel).address;
            repairLocations.put(s.address, s);
            s.external = 0;
            s.externalLabel = "";
            System.out.printf(printfmt, s.address, s.label, s.external, s.externalLabel, s.fill_value);
        }

        PrintStream symbols_out = new PrintStream(new File("output.sym"));
                String symbol_fmt = "x%04x              %-10s       %1d            %s\n";

        symbols_out.println( "ADDRESS            LABEL            EXTERNAL     EXTLABEL");
        for (Symbol s : symbolTable.values()) {
            symbols_out.printf(symbol_fmt, s.address, s.label, s.external, s.externalLabel);
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
                if (words[0].contains("ORIG")) {
                    lc = Integer.parseInt(words[1].substring(1), 16);
                }
                if (repairLocations.containsKey(lc)) {
                    obj_out.printf("x%04x\n", repairLocations.get(lc).fill_value);
                } else {
                    obj_out.println(input);
                }

                if (words[0].charAt(0) == 'x') {
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
