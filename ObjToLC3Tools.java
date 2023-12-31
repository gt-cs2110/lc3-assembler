import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteOrder;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converts human-readable Pulkit object files to human-unreadable LC3Tools
 * object files, and vice versa.
 *
 * You shouldn't read this unless you are a masochist. But you can run it with
 * `java ObjToLC3Tools bubba.obj', where `bubba.obj' was generated by saying
 * `java LC3asm bubba.asm'. It will produce a `bubba.lc3tools.obj` you can open
 * and run in LC3Tools. You can convert the other way around (from LC3Tools to
 * Pulkit) like `java ObjToLC3Tools -v bubba.lc3tools.obj`.
 *
 * Author: Austin J. Adams IV, B.S., M.S., Esq, IV
 */
public class ObjToLC3Tools {
    // Taken from:
    // https://github.com/gt-cs2110/lc3tools/blob/3929c8ad9cb89013b7084b1e10b1b3a24ad82953/src/backend/utils.cpp#L11-L12
    static final byte[] LC3TOOLS_OBJ_MAGIC = {(byte)0x1c, (byte)0x30, (byte)0x15, (byte)0xc0, (byte)0x01};
    static final byte[] LC3TOOLS_OBJ_VERSION = {(byte)0x01, (byte)0x01};

    public static void main(String[] args) {
        if (args.length != 1 && (args.length != 2 || !args[0].equals("-v"))) {
            System.err.println("usage: java ObjToLC3Tools [-v] <path to object file>");
            System.err.println();
            System.err.println("\t-v\tConvert from LC3Tools object file to Pulkit object file instead");
            System.exit(1);
        }

        boolean pulkitToChirag = args.length == 1;
        if (pulkitToChirag) {
            System.exit(convertPulkitToChirag(args[0]));
        } else  {
            System.exit(convertChiragToPulkit(args[1]));
        }
    }

    static int convertPulkitToChirag(String objfilename) {
        if (!objfilename.endsWith(".obj")) {
            System.err.println("Filename " + objfilename + " does not end in .obj. Please pass the path to an object file");
            return 1;
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
        return 0;
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

    static int convertChiragToPulkit(String objfilename) {
        String newObjFileName;
        if (objfilename.endsWith(".lc3tools.obj")) {
            newObjFileName = objfilename.substring(0, objfilename.length()-".lc3tools.obj".length()) + ".obj";
        } else if (objfilename.endsWith(".obj")) {
            newObjFileName = objfilename.substring(0, objfilename.length()-".obj".length()) + ".pulkit.obj";
        } else {
            System.err.println("Filename " + objfilename + " does not end in .obj. Please pass the path to an LC3Tools object file");
            return 1;
        }
        File objFile = new File(objfilename);
        File newObjFile = new File(newObjFileName);

        try (FileInputStream is = new FileInputStream(objFile);
             PrintStream out = new PrintStream(newObjFile)) {
            if (verifyLC3ToolsMagicNumber(is) != 0) {
                return 1;
            }

            Optional<MemLocation> loc;
            while ((loc = MemLocation.fromStream(is)).isPresent()) {
                out.println(loc.get().toString());
            }
        } catch (IOException err) {
            // Rethrow as unchecked (Dr. Mr. Gosling you are my HERO)
            throw new RuntimeException(err);
        }

        return 0;
    }

    static int verifyLC3ToolsMagicNumber(InputStream is) {
        byte[] buf = new byte[LC3TOOLS_OBJ_MAGIC.length + LC3TOOLS_OBJ_VERSION.length];
        int off = 0;
        int len = buf.length;
        do {
            int ret;
            try {
                ret = is.read(buf, off, len);
            } catch (IOException err) {
                // Rethrow as unchecked (Dr. Gosling you are brilliant thank you papa)
                throw new RuntimeException(err);
            }
            if (ret < 0) {
                System.err.println("Object file is too short");
                return 1;
            }
            off += ret;
            len -= ret;
        } while (off < buf.length);

        if (!Arrays.equals(LC3TOOLS_OBJ_MAGIC, Arrays.copyOfRange(buf, 0, LC3TOOLS_OBJ_MAGIC.length))) {
            System.err.println("Not an LC3Tools object file");
            return 1;
        }
        if (!Arrays.equals(LC3TOOLS_OBJ_VERSION, Arrays.copyOfRange(buf, LC3TOOLS_OBJ_MAGIC.length, buf.length))) {
            System.err.println("LC3Tools object file is of an unsupported version");
            return 1;
        }

        return 0;
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

        // See below in toBytes() where this is used
        static final int HEADER_LEN = 2 + 1 + 4;

        MemLocation(int value, String line, boolean is_orig) {
            this.value = value;
            this.line = line;
            this.is_orig = is_orig;
        }

        // Pulkit assembler format
        @Override
        public String toString() {
            return String.format("%sx%04x", is_orig? "ORIG: " : "", value);
        }

        // Based on:
        // https://github.com/gt-cs2110/lc3tools/blob/3929c8ad9cb89013b7084b1e10b1b3a24ad82953/src/backend/mem.cpp#L17-L25
        byte[] toBytes() {
            // This calculation is yoinked from their code. To quote the
            // LC3Tools master troll (link above):
            // > encoding (2 bytes), then orig bool (1 byte), then number of
            // > characters (4 bytes), then actual line (N bytes, not null
            // > terminated)
            int num_bytes = HEADER_LEN + line.length();
            byte[] buf = new byte[num_bytes];

            endianWrite(buf, 0, value, 2);
            buf[2] = (byte)(is_orig? 1 : 0);
            endianWrite(buf, 3, line.length(), 4);
            for (int i = 0; i < line.length(); i++) {
                buf[7 + i] = (byte)line.charAt(i);
            }

            return buf;
        }

        // Returns empty on EOF
        static Optional<MemLocation> fromStream(InputStream is) {
            byte[] buf = new byte[MemLocation.HEADER_LEN];
            int off = 0;
            int len = buf.length;
            do {
                int ret;
                try {
                    ret = is.read(buf, off, len);
                } catch (IOException err) {
                    // Rethrow as unchecked (I am a James Gosling superfan)
                    throw new RuntimeException(err);
                }
                if (ret < 0) {
                    if (off == 0) {
                        return Optional.empty();
                    } else {
                        throw new IllegalArgumentException("LC3Tools object file has incomplete MemLocation header");
                    }
                }
                off += ret;
                len -= ret;
            } while (off < buf.length);

            int value = endianRead(buf, 0, 2);
            boolean isOrig = buf[2] == (byte)1;
            int lineLength = endianRead(buf, HEADER_LEN - 4, 4);

            buf = new byte[lineLength];
            off = 0;
            len = buf.length;
            do {
                int ret;
                try {
                    ret = is.read(buf, off, len);
                } catch (IOException err) {
                    // Rethrow as unchecked (I worship James Gosling daily)
                    throw new RuntimeException(err);
                }
                if (ret < 0) {
                    throw new IllegalArgumentException("LC3Tools object file has incomplete source line");
                }
                off += ret;
                len -= ret;
            } while (off < buf.length);

            String line = new String(buf);
            return Optional.of(new MemLocation(value, line, isOrig));
        }

        // We need to write bytes to the buffer in a way that is aware of the
        // endianness of this machine (like the actual computer this is running
        // on, not the LC-3), since I assume the machine used to generate the
        // LC3Tools object file is the same machine where LC3Tools will open
        // the object file. In the words of Chirag himself (link above),
        // > this is extrememly[sic] unportable, namely because it relies on
        // > the endianness not changing
        static void endianWrite(byte[] buf, int off, int value, int len) {
            boolean big_endian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);

            for (int i = 0; i < len; i++) {
                byte b = (byte)((value >> (i*8)) & 0xff);
                int idx = off + (big_endian? len-1-i : i);
                buf[idx] = b;
            }
        }

        // See comment above for endianWrite(). Thank you Mr. Chirag
        static int endianRead(byte[] buf, int off, int len) {
            boolean big_endian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);

            int ret = 0;
            for (int i = 0; i < len; i++) {
                int idx = off + (big_endian? len-1-i : i);
                byte b = buf[idx];
                // The mask here is necessary because Java tries to be helpful
                // and sign extend the byte to an int
                ret |= ((int)b & 0xff) << (i*8);
            }
            return ret;
        }
    }
}
