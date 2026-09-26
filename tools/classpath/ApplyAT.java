import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Apply NeoForge's access transformers to a jar, so javac sees what a mod
 * sees at runtime.
 *
 * A dev environment's Minecraft is not vanilla Minecraft: NeoForge widens a
 * few hundred members before a mod ever compiles against it. Type-checking
 * against the untransformed jar reports errors that do not exist -- and,
 * worse, only some of them, so the build looks nearly right.
 */
public final class ApplyAT {

    /** What a line asks for. */
    record Want(int access, boolean unfinal) {}

    static final Map<String, Want> CLASSES = new HashMap<>();
    static final Map<String, Want> MEMBERS = new HashMap<>();   // owner + " " + name(+desc)
    static final Map<String, Want> ALL_FIELDS = new HashMap<>();
    static final Map<String, Want> ALL_METHODS = new HashMap<>();

    static int accessOf(String word) {
        return switch (word) {
            case "public" -> Opcodes.ACC_PUBLIC;
            case "protected" -> Opcodes.ACC_PROTECTED;
            case "private" -> Opcodes.ACC_PRIVATE;
            case "default" -> 0;
            default -> -1;
        };
    }

    static int widen(int flags, Want want) {
        int cleared = flags & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        int made = cleared | want.access();
        if (want.unfinal()) {
            made &= ~Opcodes.ACC_FINAL;
        }
        return made;
    }

    static void read(Path cfg) throws IOException {
        for (String raw : Files.readAllLines(cfg)) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] bits = line.split("\\s+");
            String word = bits[0];
            boolean unfinal = word.endsWith("-f");
            if (unfinal) word = word.substring(0, word.length() - 2);
            int access = accessOf(word);
            if (access < 0 || bits.length < 2) continue;
            Want want = new Want(access, unfinal);
            String owner = bits[1].replace('.', '/');
            if (bits.length == 2) {
                CLASSES.put(owner, want);
            } else if (bits[2].equals("*")) {
                ALL_FIELDS.put(owner, want);
            } else if (bits[2].equals("*()")) {
                ALL_METHODS.put(owner, want);
            } else {
                String member = bits[2];
                if (bits.length > 3) member = member + bits[3];
                MEMBERS.put(owner + " " + member, want);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        for (int at = 2; at < args.length; at++) {
            read(Path.of(args[at]));
        }
        System.out.println("  " + CLASSES.size() + " class rule(s), " + MEMBERS.size()
                + " member rule(s), " + ALL_FIELDS.size() + " all-field(s), "
                + ALL_METHODS.size() + " all-method(s)");
        int touched = 0;
        try (ZipFile in = new ZipFile(args[0]);
             ZipOutputStream out = new ZipOutputStream(
                     new BufferedOutputStream(new FileOutputStream(args[1])))) {
            Enumeration<? extends ZipEntry> every = in.entries();
            while (every.hasMoreElements()) {
                ZipEntry entry = every.nextElement();
                if (entry.isDirectory()) continue;
                byte[] body = in.getInputStream(entry).readAllBytes();
                if (entry.getName().endsWith(".class")) {
                    byte[] made = transform(body);
                    if (made != null) { body = made; touched++; }
                }
                ZipEntry fresh = new ZipEntry(entry.getName());
                fresh.setTime(0L);
                out.putNextEntry(fresh);
                out.write(body);
                out.closeEntry();
            }
        }
        System.out.println("  " + touched + " class(es) rewritten into " + args[1]);
    }

    static byte[] transform(byte[] body) {
        ClassReader reader = new ClassReader(body);
        String name = reader.getClassName();
        boolean[] changed = {false};
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String owner, String signature,
                              String superName, String[] interfaces) {
                Want want = CLASSES.get(owner);
                if (want != null) { access = widen(access, want); changed[0] = true; }
                super.visit(version, access, owner, signature, superName, interfaces);
            }

            @Override
            public void visitInnerClass(String owner, String outer, String inner, int access) {
                Want want = CLASSES.get(owner);
                if (want != null) { access = widen(access, want); changed[0] = true; }
                super.visitInnerClass(owner, outer, inner, access);
            }

            @Override
            public FieldVisitor visitField(int access, String field, String desc,
                                           String signature, Object value) {
                Want want = MEMBERS.get(name + " " + field);
                if (want == null) want = ALL_FIELDS.get(name);
                if (want != null) { access = widen(access, want); changed[0] = true; }
                return super.visitField(access, field, desc, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String method, String desc,
                                             String signature, String[] thrown) {
                Want want = MEMBERS.get(name + " " + method + desc);
                if (want == null) want = ALL_METHODS.get(name);
                if (want != null) { access = widen(access, want); changed[0] = true; }
                return super.visitMethod(access, method, desc, signature, thrown);
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : null;
    }
}
