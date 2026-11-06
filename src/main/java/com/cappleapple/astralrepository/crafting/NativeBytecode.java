package com.cappleapple.astralrepository.crafting;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/** Read-only optional-mod contract discovery without linking unrelated client-only signatures. */
public final class NativeBytecode {
    private NativeBytecode() {}

    public record Reference(int opcode, String owner, String name, String descriptor) {
        public boolean staticRead() { return opcode == 178; }
        public Object readStatic(ClassLoader loader) {
            if (!staticRead()) throw new IllegalArgumentException("Not a static field read");
            try {
                Class<?> declaring = Class.forName(owner, false, loader);
                Class<?> type = MethodType.fromMethodDescriptorString("()" + descriptor, loader).returnType();
                return MethodHandles.privateLookupIn(declaring, MethodHandles.lookup())
                        .findStaticVarHandle(declaring, name, type).get();
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("Native field unavailable: " + owner + "." + name, failure);
            }
        }
        public Object readInstance(Object instance) {
            if (opcode != 180) throw new IllegalArgumentException("Not an instance field read");
            try {
                ClassLoader loader = instance.getClass().getClassLoader();
                Class<?> declaring = Class.forName(owner, false, loader);
                Class<?> type = MethodType.fromMethodDescriptorString("()" + descriptor, loader).returnType();
                return MethodHandles.privateLookupIn(declaring, MethodHandles.lookup())
                        .findVarHandle(declaring, name, type).get(instance);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("Native field unavailable: " + owner + "." + name, failure);
            }
        }
    }

    public record Field(String name, String descriptor, String signature, int access, Object constant) {}
    public record Dynamic(int bootstrap, String name, String descriptor) {}
    public record SwitchTargets(Map<Integer,Integer> targets) {
        public SwitchTargets { targets=Map.copyOf(targets); }
    }
    public record Instruction(int offset, int opcode, int operand, Object constant, Reference reference) {}
    public record Method(String name, String descriptor, String signature, int access,
                         List<Instruction> instructions) {
        public Method { instructions = List.copyOf(instructions); }
        public List<Reference> references() {
            return instructions.stream().map(Instruction::reference).filter(Objects::nonNull).toList();
        }
    }
    public record Shape(String name, String parent, List<Field> fields, List<Method> methods) {
        public Shape { fields = List.copyOf(fields); methods = List.copyOf(methods); }
        public List<Reference> references() {
            return methods.stream().flatMap(method -> method.references().stream()).distinct().toList();
        }
    }

    private static final ClassValue<Shape> CACHE = new ClassValue<>() {
        @Override protected Shape computeValue(Class<?> type) {
            String resource = "/" + type.getName().replace('.', '/') + ".class";
            try (InputStream input = type.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Missing class resource " + resource);
                return read(input);
            } catch (IOException | RuntimeException failure) {
                throw new IllegalStateException("Cannot inspect native contract " + type.getName(), failure);
            }
        }
    };

    public static Shape inspect(Class<?> type) { return CACHE.get(type); }
    public static List<Shape> hierarchy(Class<?> type) {
        List<Shape> result = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass())
            result.add(inspect(current));
        return List.copyOf(result);
    }

    /** Accepts a class-file stream; does not load, initialize, or execute its declaring class. */
    public static Shape read(InputStream source) throws IOException {
        DataInputStream input = new DataInputStream(new BufferedInputStream(source));
        if (input.readInt() != 0xcafebabe) throw new IOException("Invalid class file");
        input.readUnsignedShort(); input.readUnsignedShort();
        Object[] pool = new Object[input.readUnsignedShort()];
        for (int index = 1; index < pool.length; index++) {
            int tag = input.readUnsignedByte();
            pool[index] = switch (tag) {
                case 1 -> input.readUTF();
                case 3 -> input.readInt();
                case 4 -> input.readFloat();
                case 5 -> { long value = input.readLong(); index++; yield value; }
                case 6 -> { double value = input.readDouble(); index++; yield value; }
                case 7, 8, 16, 19, 20 -> new Entry(tag, input.readUnsignedShort(), 0);
                case 9, 10, 11, 12, 17, 18 -> new Entry(tag, input.readUnsignedShort(), input.readUnsignedShort());
                case 15 -> new Entry(tag, input.readUnsignedByte(), input.readUnsignedShort());
                default -> throw new IOException("Unknown constant-pool tag " + tag);
            };
        }
        input.readUnsignedShort();
        String name = className(pool, input.readUnsignedShort());
        int parentIndex = input.readUnsignedShort();
        String parent = parentIndex == 0 ? "" : className(pool, parentIndex);
        int interfaces = input.readUnsignedShort();
        input.skipNBytes(2L * interfaces);
        List<Field> fields = new ArrayList<>();
        int count = input.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            int access = input.readUnsignedShort();
            String fieldName = utf(pool, input.readUnsignedShort()), descriptor = utf(pool, input.readUnsignedShort());
            Map<String, byte[]> attributes = attributes(input, pool);
            fields.add(new Field(fieldName, descriptor, signature(attributes, pool), access,
                    attributes.containsKey("ConstantValue") ? constant(pool, unsignedShort(attributes.get("ConstantValue"), 0)) : null));
        }
        List<Method> methods = new ArrayList<>();
        count = input.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            int access = input.readUnsignedShort();
            String methodName = utf(pool, input.readUnsignedShort()), descriptor = utf(pool, input.readUnsignedShort());
            Map<String, byte[]> attributes = attributes(input, pool);
            List<Instruction> instructions = List.of();
            if (attributes.containsKey("Code")) {
                try (DataInputStream code = new DataInputStream(new ByteArrayInputStream(attributes.get("Code")))) {
                    code.readUnsignedShort(); code.readUnsignedShort();
                    int length = code.readInt();
                    if (length < 0 || length > 65535) throw new IOException("Invalid code length");
                    byte[] bytes = code.readNBytes(length);
                    if (bytes.length != length) throw new EOFException();
                    instructions = instructions(bytes, pool);
                }
            }
            methods.add(new Method(methodName, descriptor, signature(attributes, pool), access, instructions));
        }
        Map<Integer, Reference> lambdas = lambdas(attributes(input, pool), pool);
        if (!lambdas.isEmpty()) methods = methods.stream().map(method -> new Method(method.name(), method.descriptor(), method.signature(), method.access(),
                method.instructions().stream().map(instruction -> instruction.constant() instanceof Dynamic dynamic && lambdas.containsKey(dynamic.bootstrap())
                        ? new Instruction(instruction.offset(), instruction.opcode(), instruction.operand(), instruction.constant(), lambdas.get(dynamic.bootstrap())) : instruction).toList())).toList();
        return new Shape(name, parent, fields, methods);
    }

    private record Entry(int tag, int first, int second) {}
    private static String utf(Object[] pool, int index) throws IOException {
        if (index <= 0 || index >= pool.length || !(pool[index] instanceof String value)) throw new IOException("Invalid UTF constant");
        return value;
    }
    private static String className(Object[] pool, int index) throws IOException {
        if (!(pool[index] instanceof Entry entry) || entry.tag != 7) throw new IOException("Invalid class constant");
        return utf(pool, entry.first).replace('/', '.');
    }
    private static Object constant(Object[] pool, int index) throws IOException {
        Object value = pool[index];
        if (value instanceof Entry entry) {
            if (entry.tag == 8) return utf(pool, entry.first);
            if (entry.tag == 7) return className(pool, index);
            return null;
        }
        return value;
    }
    private static Map<String, byte[]> attributes(DataInputStream input, Object[] pool) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        int count = input.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            String name = utf(pool, input.readUnsignedShort());
            int length = input.readInt();
            if (length < 0 || length > 16 * 1024 * 1024) throw new IOException("Invalid attribute length");
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) throw new EOFException();
            result.put(name, bytes);
        }
        return result;
    }
    private static String signature(Map<String, byte[]> attributes, Object[] pool) throws IOException {
        return attributes.containsKey("Signature") ? utf(pool, unsignedShort(attributes.get("Signature"), 0)) : "";
    }
    private static int unsignedShort(byte[] bytes, int offset) throws IOException {
        if (offset < 0 || offset + 2 > bytes.length) throw new EOFException();
        return (bytes[offset] & 255) << 8 | bytes[offset + 1] & 255;
    }
    private static int integer(byte[] bytes, int offset) throws IOException {
        return unsignedShort(bytes, offset) << 16 | unsignedShort(bytes, offset + 2);
    }
    private static Map<Integer, Reference> lambdas(Map<String, byte[]> attributes, Object[] pool) throws IOException {
        Map<Integer, Reference> result = new HashMap<>();
        if (!attributes.containsKey("BootstrapMethods")) return result;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(attributes.get("BootstrapMethods")))) {
            int count = input.readUnsignedShort();
            for (int index = 0; index < count; index++) {
                Entry handle = (Entry) pool[input.readUnsignedShort()];
                Reference bootstrap = memberReference(pool, handle.second, 186);
                boolean lambda = bootstrap.owner().equals("java.lang.invoke.LambdaMetafactory");
                int arguments = input.readUnsignedShort();
                for (int argument = 0; argument < arguments; argument++) {
                    Object entry = pool[input.readUnsignedShort()];
                    if (lambda && entry instanceof Entry implementation && implementation.tag == 15)
                        result.put(index, memberReference(pool, implementation.second, 186));
                }
            }
        }
        return result;
    }
    private static Reference memberReference(Object[] pool, int index, int opcode) throws IOException {
        if (!(pool[index] instanceof Entry member) || member.tag < 9 || member.tag > 11) throw new IOException("Invalid member reference");
        Entry type = (Entry) pool[member.second];
        return new Reference(opcode, className(pool, member.first), utf(pool, type.first), utf(pool, type.second));
    }
    private static List<Instruction> instructions(byte[] bytes, Object[] pool) throws IOException {
        List<Instruction> result = new ArrayList<>();
        for (int offset = 0; offset < bytes.length;) {
            int start = offset, opcode = bytes[offset++] & 255, operand = 0, length = 0;
            Object constant = null;
            Reference reference = null;
            if (opcode >= 2 && opcode <= 8) constant = opcode - 3;
            else if (opcode == 9 || opcode == 10) constant = (long) opcode - 9;
            else if (opcode >= 11 && opcode <= 13) constant = (float) opcode - 11;
            else if (opcode == 14 || opcode == 15) constant = (double) opcode - 14;
            else if (opcode == 16) { length = 1; constant = (int) bytes[offset]; }
            else if (opcode == 17) { length = 2; constant = (int) (short) unsignedShort(bytes, offset); }
            else if (opcode >= 18 && opcode <= 20) {
                length = opcode == 18 ? 1 : 2;
                operand = length == 1 ? bytes[offset] & 255 : unsignedShort(bytes, offset);
                constant = constant(pool, operand);
            } else if (opcode >= 21 && opcode <= 25 || opcode >= 54 && opcode <= 58 || opcode == 169 || opcode == 188) {
                length = 1; operand = bytes[offset] & 255;
            } else if (opcode == 132) { length = 2; operand = bytes[offset] & 255; constant = (int) bytes[offset + 1]; }
            else if (opcode >= 153 && opcode <= 168 || opcode == 198 || opcode == 199) {
                length = 2; operand = start + (short) unsignedShort(bytes, offset);
            } else if (opcode == 170 || opcode == 171) {
                int aligned = (offset + 3) & ~3;
                operand=start+integer(bytes,aligned);
                Map<Integer,Integer> targets=new LinkedHashMap<>();
                if (opcode == 170) {
                    int low = integer(bytes, aligned + 4), high = integer(bytes, aligned + 8);
                    long size = 12L + 4L * (high - (long) low + 1);
                    if (high < low || size > bytes.length) throw new IOException("Invalid tableswitch");
                    for(long key=low;key<=high;key++) targets.put((int)key,start+integer(bytes,aligned+12+4*(int)(key-low)));
                    length = aligned - offset + (int) size;
                } else {
                    int pairs = integer(bytes, aligned + 4);
                    long size = 8L + 8L * pairs;
                    if (pairs < 0 || size > bytes.length) throw new IOException("Invalid lookupswitch");
                    for(int pair=0;pair<pairs;pair++) {
                        int key=integer(bytes,aligned+8+8*pair),target=start+integer(bytes,aligned+12+8*pair);
                        if(targets.put(key,target)!=null) throw new IOException("Duplicate lookupswitch key");
                    }
                    length = aligned - offset + (int) size;
                }
                constant=new SwitchTargets(targets);
            } else if (opcode >= 178 && opcode <= 185) {
                length = opcode == 185 ? 4 : 2;
                operand = unsignedShort(bytes, offset);
                reference = memberReference(pool, operand, opcode);
            } else if (opcode == 186) {
                length = 4; operand = unsignedShort(bytes, offset);
                Entry dynamic = (Entry) pool[operand], type = (Entry) pool[dynamic.second];
                constant = new Dynamic(dynamic.first, utf(pool, type.first), utf(pool, type.second));
            }
            else if (opcode == 187 || opcode == 189 || opcode == 192 || opcode == 193) {
                length = 2; operand = unsignedShort(bytes, offset); constant = className(pool, operand);
            } else if (opcode == 196) {
                int widened = bytes[offset] & 255;
                if (!(widened >= 21 && widened <= 25 || widened >= 54 && widened <= 58 || widened == 132 || widened == 169)) throw new IOException("Invalid wide instruction");
                length = widened == 132 ? 5 : 3;
            } else if (opcode == 197) length = 3;
            else if (opcode == 200 || opcode == 201) { length = 4; operand = start + integer(bytes, offset); }
            else if (opcode > 201) throw new IOException("Unknown instruction " + opcode);
            if ((long) offset + length > bytes.length) throw new EOFException();
            result.add(new Instruction(start, opcode, operand, constant, reference));
            offset += length;
        }
        return List.copyOf(result);
    }
}
