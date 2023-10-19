import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts human-readable Pulkit object files to human-unreadable LC3Tools
 * object files.
 *
 * You shouldn't read this unless you are a masochist. But you can run it with
 * `java ObjToLC3Tools bubba.obj', where `bubba.obj' was generated by saying
 * `java LC3asm bubba.asm'. It will produce a `bubba.lc3tools.obj` you can open
 * and run in LC3Tools.
 *
 * Author: Austin J. Adams IV, B.S., M.S., Esq, IV
 */
public class ObjToLC3Tools {
    // Taken from:
    // https://github.com/gt-cs2110/lc3tools/blob/3929c8ad9cb89013b7084b1e10b1b3a24ad82953/src/backend/utils.cpp#L11-L12
    static final byte[] LC3TOOLS_OBJ_MAGIC = {(byte)0x1c, (byte)0x30, (byte)0x15, (byte)0xc0, (byte)0x01};
    static final byte[] LC3TOOLS_OBJ_VERSION = {(byte)0x01, (byte)0x01};

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: java ObjToLC3Tools <local path to object file>");
            System.exit(1);
        }

        String objfilename = args[0];
        if (!objfilename.endsWith(".obj")) {
            System.err.println("Filename " + objfilename + " does not end in .obj. Please pass the path to an object file");
        }
        String filebase = objfilename.substring(0, objfilename.lastIndexOf('.')); // get the root filename without any extensions
        File dbgsymFile = new File(filebase + ".dbgsym"); // find debug symbols
        File objFile = new File(objfilename); // object file
        File newObjFile = new File(filebase + ".lc3tools.obj"); // lc3tools object file

        // mapping from addresses to line of source code
        Map<Integer, String> debugSymbols = parseDebugSymbols(dbgsymFile);
        // Object file as a data structure
        List<MemLocation> obj = parseObjFile(objFile, debugSymbols);

        try (FileOutputStream os = new FileOutputStream(newObjFile)) {
            os.write(LC3TOOLS_OBJ_MAGIC);
            os.write(LC3TOOLS_OBJ_VERSION);

            for (MemLocation memloc : obj) {
                os.write(memloc.toBytes());
            }
        } catch (IOException err) {
            // Rethrow as unchecked (Thank you Mr. Dr. Gosling sir)
            throw new RuntimeException(err);
        }

        System.out.println("Wrote LC3Tools object file to " + newObjFile.getPath());
    }

    static Map<Integer, String> parseDebugSymbols(File dbgsymFile) {
        try (FileReader fr = new FileReader(dbgsymFile);
             BufferedReader br = new BufferedReader(fr)) {
            return br.lines()
                     .map(String::trim)
                     .filter(line -> !line.isEmpty())
                     .map(line -> {
                        String[] splat = line.split(": ", 2);
                        String addrString = splat[0];
                        if (addrString.startsWith("x")) {
                            addrString = addrString.substring(1);
                        }
                        Integer addr = Integer.parseInt(addrString, 16);
                        String codeLine = "";
                        if (splat.length > 1) {
                            codeLine = splat[1];
                        }
                        return new AbstractMap.SimpleImmutableEntry<Integer, String>(addr, codeLine);
                     })
                     .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry<Integer, String>::getKey,
                                               AbstractMap.SimpleImmutableEntry<Integer, String>::getValue));
        } catch (IOException err) {
            // Rethrow as unchecked (Thank you Mr. Dr. Gosling sir)
            throw new RuntimeException(err);
        }
    }

    static List<MemLocation> parseObjFile(File objFile, Map<Integer, String> debugSymbols) {
        final String ORIG = "ORIG: ";
        int nextAddr = -1;
        List<MemLocation> result = new ArrayList<MemLocation>();

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

                String codeLine;
                if (isOrig) {
                    codeLine = "";
                    nextAddr = value;
                } else {
                    if (nextAddr < 0) {
                        throw new IllegalStateException("Word before .orig");
                    }
                    codeLine = debugSymbols.getOrDefault(nextAddr++, "");
                }
                result.add(new MemLocation(value, codeLine, isOrig));
            }
        } catch (IOException err) {
            // Rethrow as unchecked (Thank you Mr. Dr. Gosling sir)
            throw new RuntimeException(err);
        }

        return result;
    }

    // Mirrors the MemLocation class in the LC3Tools source code (don't read it
    // unless you want mental health issues):
    // https://github.com/gt-cs2110/lc3tools/blob/3929c8ad9cb89013b7084b1e10b1b3a24ad82953/src/backend/mem.h
    static class MemLocation {
        int value;
        String line;
        // In an LC3Tools object file, an .orig is represented as a memory
        // location with is_orig=true and value set to the start address
        boolean is_orig;

        MemLocation(int value, String line, boolean is_orig) {
            this.value = value;
            this.line = line;
            this.is_orig = is_orig;
        }

        // Based on:
        // https://github.com/gt-cs2110/lc3tools/blob/3929c8ad9cb89013b7084b1e10b1b3a24ad82953/src/backend/mem.cpp#L17-L25
        byte[] toBytes() {
            // This calculation is yoinked from their code. To quote the
            // LC3Tools master troll (link above):
            // > encoding (2 bytes), then orig bool (1 byte), then number of
            // > characters (4 bytes), then actual line (N bytes, not null
            // > terminated)
            int num_bytes = 2 + 1 + 4 + line.length();
            byte[] buf = new byte[num_bytes];

            endian_write(buf, 0, value, 2);
            buf[2] = (byte)(is_orig? 1 : 0);
            endian_write(buf, 3, line.length(), 4);
            for (int i = 0; i < line.length(); i++) {
                buf[7 + i] = (byte)line.charAt(i);
            }

            return buf;
        }

        // We need to write bytes to the buffer in a way that is aware of the
        // endianness of this machine (like the actual computer this is running
        // on, not the LC-3), since I assume the machine used to generate the
        // LC3Tools object file is the same machine where LC3Tools will open
        // the object file. In the words of Chirag himself (link above),
        // > this is extrememly[sic] unportable, namely because it relies on
        // > the endianness not changing
        private static void endian_write(byte[] buf, int off, int value, int len) {
            boolean big_endian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);

            for (int i = 0; i < len; i++) {
                byte b = (byte)((value >> (i*8)) & 0xff);
                int idx = off + (big_endian? len-1-i : i);
                buf[idx] = b;
            }
        }
    }
}
