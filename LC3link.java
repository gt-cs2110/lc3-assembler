import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.io.File;


public class LC3link {

    static class ORIG_BLOCK {
        private int orig;
        private List<Integer> memory;

        public ORIG_BLOCK(int start) {
            orig = start;
            memory = new ArrayList<>();
        }

    }

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

    static Map<Integer, ORIG_BLOCK> chunks = new HashMap<>();
    static List<Integer> origs = new ArrayList<>();


    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("error incorrect inputs. Usage:");
            System.out.println("java Lc3Link <file1> <file2> ... <file3>");
            System.exit(1);
        }

        // go through all the filenames and build the aggregate symbol table
        for (String filename : args) {
            String symbol_filename = filename + ".sym";
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

        String fmt = "x%04x              %-10s       %1d            %s\n"; // a string format for printing the individual symbols
        for (Symbol s : symbolTable.values()) {
            System.out.printf(fmt, s.address, s.label, s.external, s.externalLabel);
        }

        System.out.println("Extern Users: " + extern_users.toString());

        System.out.println("resolving extern in symbol table");
        String fmt2 = "x%04x              %-10s       %1d            %-10s          x%04x\n";
        for (String user : extern_users) {
            Symbol s = symbolTable.get(user);
            s.fill_value = symbolTable.get(s.externalLabel).address;
            System.out.printf(fmt2, s.address, s.label, s.external, s.externalLabel, s.fill_value);
        }



    }
}