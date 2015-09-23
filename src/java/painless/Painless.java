package painless;

public final class Painless {
    public static void main(String args[]) throws Exception {
        final long start = System.currentTimeMillis();
        PainlessExecutable executable = compile("test", "int x = 0; while (true) {x = x + 1; if (x >= 5) continue; if (x <= 6) {break;}}");
        //PainlessExecutable executable = compile("test", "for (int x = 0; x < 5; x = x + 1);");
        final long end = System.currentTimeMillis() - start;
        //Object value = executable.execute(null);
        System.out.println(end/1000.0);
    }

    public static PainlessExecutable compile(String name, String source) {
        return compile(name, source, Painless.class.getClassLoader());
    }

    public static PainlessExecutable compile(String name, String source, ClassLoader parent) {
        return PainlessCompiler.compile(name, source, parent);
    }

    private Painless() {}
}
