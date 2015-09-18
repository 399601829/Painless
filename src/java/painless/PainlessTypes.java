package painless;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

class PainlessTypes {
    static class PConstructor {
        final String pname;
        final PClass powner;
        final Method amethod;
        final Constructor jconstructor;

        private PConstructor(final String pname, final PClass powner, Method amethod, Constructor jconstructor) {
            this.pname = pname;
            this.powner = powner;
            this.amethod = amethod;
            this.jconstructor = jconstructor;
        }
    }

    static class PMethod {
        final String pname;
        final PClass powner;
        final Method amethod;
        final java.lang.reflect.Method jmethod;

        private PMethod(final String pname, final PClass powner, Method amethod, java.lang.reflect.Method jmethod) {
            this.pname = pname;
            this.powner = powner;
            this.amethod = amethod;
            this.jmethod = jmethod;
        }
    }

    static class PField {
        final String pname;
        final PClass powner;
        final Type atype;
        final Field jfield;

        private PField(final String pname, final PClass powner, final Type atype, final Field jfield) {
            this.pname = pname;
            this.powner = powner;
            this.atype = atype;
            this.jfield = jfield;
        }
    }

    static class PClass {
        final String pname;
        final Type atype;
        final Class clazz;

        private final Map<String, PMethod> pfunctions;
        private final Map<String, PConstructor> pconstructors;
        private final Map<String, PMethod> pmethods;

        private final Map<String, PField> pstatics;
        private final Map<String, PField> pmembers;

        private PClass(final String pname, final Type atype, final Class clazz) {
            this.pname = pname;
            this.atype = atype;
            this.clazz = clazz;

            this.pfunctions = new HashMap<>();
            this.pconstructors = new HashMap<>();
            this.pmethods = new HashMap<>();

            this.pstatics = new HashMap<>();
            this.pmembers = new HashMap<>();
        }

        PMethod getPFunction(String pfunction) {
            return pfunctions.get(pfunction);
        }

        PConstructor getPConstructor(String pconstructor) {
            return pconstructors.get(pconstructor);
        }

        PMethod getPMethod(String pmethod) {
            return pmethods.get(pmethod);
        }

        PField getPStatic(String pstatic) {
            return pstatics.get(pstatic);
        }

        PField getPMember(String pmember) {
            return pmembers.get(pmember);
        }
    }

    static class PCast {
        final Type afrom;
        final Type ato;

        PCast(final Type afrom, final Type ato) {
            this.afrom = afrom;
            this.ato = ato;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }

            if (object == null || getClass() != object.getClass()) {
                return false;
            }

            final PCast pCast = (PCast)object;

            if (!afrom.equals(pCast.afrom)) {
                return false;
            }

            return ato.equals(pCast.ato);
        }

        @Override
        public int hashCode() {
            int result = afrom.hashCode();
            result = 31 * result + ato.hashCode();

            return result;
        }
    }

    static class PTransform {
        final PCast pcast;
        final PMethod pmethod;
        final int level;

        PTransform(final PCast pcast, final PMethod pmethod, final int level) {
            this.pcast = pcast;
            this.pmethod = pmethod;
            this.level = level;
        }
    }

    static Type getATypeFromJClass(final String jtype) {
        int index = jtype.indexOf('[');
        String type = jtype;
        String dimensions = "";

        if (index != -1) {
            type = jtype.substring(0, index);
            final int length = jtype.length();

            while (index < length) {
                if (jtype.charAt(index) == '[' && ++index < length && jtype.charAt(index++) == ']') {
                    dimensions += "[";
                } else {
                    throw new IllegalArgumentException();
                }
            }
        }

        switch (type) {
            case "void":
                type = "V";
                break;
            case "boolean":
                type = "Z";
                break;
            case "byte":
                type = "B";
                break;
            case "short":
                type = "S";
                break;
            case "char":
                type = "C";
                break;
            case "int":
                type = "I";
                break;
            case "long":
                type = "J";
                break;
            case "float":
                type = "F";
                break;
            case "double":
                type = "D";
                break;
            default:
                type = "L" + type.replace(".", "/") + ";";
        }

        type = dimensions + type;

        return Type.getType(type);
    }

    static Class getJClass(Type atype) {
        try {
            switch (atype.getSort()) {
                case Type.VOID:
                    return void.class;
                case Type.BOOLEAN:
                    return boolean.class;
                case Type.BYTE:
                    return byte.class;
                case Type.CHAR:
                    return char.class;
                case Type.SHORT:
                    return short.class;
                case Type.INT:
                    return int.class;
                case Type.LONG:
                    return long.class;
                case Type.FLOAT:
                    return float.class;
                case Type.DOUBLE:
                    return double.class;
                default:
                    return Class.forName(atype.getInternalName().replace('/', '.'));
            }
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException();
        }
    }

    static java.lang.reflect.Constructor getJConstructor(final PClass pclass, final Type[] aarguments) {
        final Class[] jarguments = new Class[aarguments.length];

        for (int argument = 0; argument < aarguments.length; ++argument) {
            jarguments[argument] = getJClass(aarguments[argument]);
        }

        try {
            return pclass.clazz.getConstructor(jarguments);
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException();
        }
    }

    static java.lang.reflect.Method getJMethod(final PClass pclass, final String jname, final Type[] aarguments) {
        final Class[] jarguments = new Class[aarguments.length];

        for (int argument = 0; argument < aarguments.length; ++argument) {
            jarguments[argument] = getJClass(aarguments[argument]);
        }

        try {
            return pclass.clazz.getMethod(jname, jarguments);
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException();
        }
    }

    static Field getJField(final PClass pclass, final String jname) {
        try {
            return pclass.clazz.getField(jname);
        } catch (NoSuchFieldException exception) {
            throw new IllegalArgumentException();
        }
    }

    static final String PROPERTIES_FILE = PainlessTypes.class.getSimpleName() + ".properties";

    private final Map<String, PClass> pclasses;
    private final Map<Type, PClass> aclasses;
    private final Map<PCast, PMethod> ptransforms;

    PainlessTypes() {
        pclasses = new HashMap<>();
        aclasses = new HashMap<>();
        ptransforms = new HashMap<>();

        final Properties properties = new Properties();

        try (final InputStream stream = getClass().getResourceAsStream(PROPERTIES_FILE)) {
            properties.load(stream);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }

        for (final String key : properties.stringPropertyNames()) {
            if (key.startsWith("type")) {
                final String[] keysplit = key.split("\\.");
                final String name = keysplit[1];

                if (pclasses.containsKey(name)) {
                    throw new IllegalArgumentException();
                }

                final String property = properties.getProperty(key);
                final Type atype = getATypeFromJClass(property);
                final Class clazz = getJClass(atype);
                final PClass pclass = new PClass(name, atype, clazz);

                pclasses.put(name, pclass);
                aclasses.put(atype, pclass);
            } else {
                boolean valid = key.startsWith("constructor") || key.startsWith("function")   ||
                                key.startsWith("static")      || key.startsWith("method")     ||
                                key.startsWith("member")      || key.startsWith("transform")  ||
                                key.startsWith("cross");

                if (!valid) {
                    throw new IllegalArgumentException();
                }
            }
        }

        for (final String key : properties.stringPropertyNames()) {
            if (key.startsWith("constructor")) {
                final String[] keysplit = key.split("\\.");

                if (keysplit.length != 3) {
                    throw new IllegalArgumentException();
                }

                final String ptype = keysplit[1];
                final String pname = keysplit[2];
                final PClass powner = pclasses.get(ptype);

                if (powner == null) {
                    throw new IllegalArgumentException();
                }

                if (powner.pconstructors.containsKey(pname) ||
                        powner.pfunctions.containsKey(pname) ||
                        powner.pmethods.containsKey(pname)) {
                    throw new IllegalArgumentException();
                }

                String property = properties.getProperty(key);

                if (property.charAt(0) != '(' || property.charAt(property.length() - 1) != ')') {
                    throw new IllegalArgumentException();
                }

                property = property.replace("(", "").replace(")", "").replace(" ", "");
                final String[] propsplit = property.isEmpty() ? null : property.split(",");
                final Type[] aarguments = new Type[propsplit == null ? 0 : propsplit.length];

                for (int argument = 0; argument < aarguments.length; ++argument) {
                    final String pargument = propsplit[argument];
                    final Type aargument = getATypeFromPClass(pargument);
                    aarguments[argument] = aargument;
                }

                final Method amethod = new Method("<init>", Type.VOID_TYPE, aarguments);
                final Constructor jconstructor = getJConstructor(powner, aarguments);
                final PConstructor pconstructor = new PConstructor(pname, powner, amethod, jconstructor);

                powner.pconstructors.put(pname, pconstructor);
            } else if (key.startsWith("function") || key.startsWith("method")) {
                final String[] keysplit = key.split("\\.");

                if (keysplit.length != 3) {
                    throw new IllegalArgumentException();
                }

                final String ptype = keysplit[1];
                final String pname = keysplit[2];
                final PClass powner = pclasses.get(ptype);

                if (powner == null) {
                    throw new IllegalArgumentException();
                }

                if (powner.pconstructors.containsKey(pname) ||
                        powner.pfunctions.containsKey(pname) ||
                        powner.pmethods.containsKey(pname)) {
                    throw new IllegalArgumentException();
                }

                String property = properties.getProperty(key);

                int index = property.indexOf(" ");
                final Type artn = getATypeFromPClass(property.substring(0, index));
                final Class jrtn = getJClass(artn);

                property = property.substring(index + 1);
                index = property.indexOf("(");
                final String jname = property.substring(0, index);

                if ("".equals(jname)) {
                    throw new IllegalArgumentException();
                }

                property = property.substring(index);

                if (property.charAt(0) != '(' || property.charAt(property.length() - 1) != ')') {
                    throw new IllegalArgumentException();
                }

                property = property.replace("(", "").replace(")", "").replace(" ", "");
                final String[] propsplit = property.isEmpty() ? null : property.split(",");
                final Type[] aarguments = new Type[propsplit == null ? 0 : propsplit.length];

                for (int argument = 0; argument < aarguments.length; ++argument) {
                    final String pargument = propsplit[argument];
                    final Type aargument = getATypeFromPClass(pargument);
                    aarguments[argument] = aargument;
                }

                final Method amethod = new Method(jname, artn, aarguments);
                final java.lang.reflect.Method jmethod = getJMethod(powner, jname, aarguments);

                if (!jrtn.equals(jmethod.getReturnType())) {
                    throw new IllegalArgumentException();
                }

                final int jmodifiers = jmethod.getModifiers();
                final PMethod pmethod = new PMethod(pname, powner, amethod, jmethod);

                if (key.startsWith("function")) {
                    if (!Modifier.isStatic(jmodifiers)) {
                        throw new IllegalArgumentException();
                    }

                    powner.pfunctions.put(pname, pmethod);
                } else if (key.startsWith("method")) {
                    if (Modifier.isStatic(jmodifiers)) {
                        throw new IllegalArgumentException();
                    }

                    powner.pmethods.put(pname, pmethod);
                } else {
                    throw new IllegalStateException();
                }
            } else if (key.startsWith("static") || key.startsWith("member")) {
                final String[] keysplit = key.split("\\.");

                if (keysplit.length != 3) {
                    throw new IllegalArgumentException();
                }

                final String ptype = keysplit[1];
                final String pname = keysplit[2];
                final PClass powner = pclasses.get(ptype);

                if (powner == null) {
                    throw new IllegalArgumentException();
                }

                if (powner.pstatics.containsKey(pname) || powner.pmembers.containsKey(pname)) {
                    throw new IllegalArgumentException();
                }

                final String property = properties.getProperty(key);
                final String[] propsplit = property.split("\\s+");

                if (propsplit.length != 2) {
                    throw new IllegalArgumentException();
                }

                final String pmemtype = propsplit[0];
                final String jname = propsplit[1];
                final Type atype = getATypeFromPClass(pmemtype);

                if ("".equals(jname)) {
                    throw new IllegalArgumentException();
                }

                final Field jfield = getJField(powner, jname);

                if (!getJClass(atype).equals(jfield.getType())) {
                    throw new IllegalArgumentException();
                }

                final int modifiers = jfield.getModifiers();
                final PField pmember = new PField(pname, powner, atype, jfield);

                if ("static".equals(keysplit[0])) {
                    if (!Modifier.isStatic(modifiers)) {
                        throw new IllegalArgumentException();
                    }
                    powner.pstatics.put(pname, pmember);
                } else if ("member".equals(keysplit[0])) {
                    if (Modifier.isStatic(modifiers)) {
                        throw new IllegalArgumentException();
                    }

                    powner.pmembers.put(pname, pmember);
                } else {
                    throw new IllegalStateException();
                }
            }
        }

        for (final String key : properties.stringPropertyNames()) {
            if (key.startsWith("transform")) {
                final String[] keysplit = key.split("\\.");

                if (keysplit.length != 3) {
                    throw new IllegalArgumentException();
                }

                final Type afrom = getATypeFromPClass(keysplit[1]);
                final Type ato = getATypeFromPClass(keysplit[2]);

                final PCast pcast = new PCast(afrom, ato);

                final String property = properties.getProperty(key);
                final String[] propsplit = property.split("\\s+");

                if (propsplit.length != 4) {
                    throw new IllegalArgumentException();
                }

                final PClass powner = pclasses.get(propsplit[1]);

                if (powner == null) {
                    throw new IllegalArgumentException();
                }

                PMethod pmethod;

                if ("function".equals(propsplit[0])) {
                    pmethod = powner.pfunctions.get(propsplit[2]);
                } else if ("method".equals(propsplit[0])) {
                    pmethod = powner.pmethods.get(propsplit[2]);
                } else {
                    throw new IllegalArgumentException();
                }

                if (pmethod == null) {
                    throw new IllegalArgumentException();
                }

                ptransforms.put(pcast, pmethod);
            } else if (key.startsWith("cross")) {
                final String[] keysplit = key.split("\\.");

                if (keysplit.length != 4) {
                    throw new IllegalArgumentException();
                }

                final PClass powner = pclasses.get(keysplit[2]);

                if (powner == null) {
                    throw new IllegalArgumentException();
                }

                final String property = properties.getProperty(key);
                final String[] propsplit = property.split("\\s+");

                if (propsplit.length != 2) {
                    throw new IllegalArgumentException();
                }

                final PClass porigin = pclasses.get(propsplit[0]);

                if (porigin == null) {
                    throw new IllegalArgumentException();
                }

                if ("function".equals(keysplit[1])) {
                    PMethod pfunction = porigin.pfunctions.get(propsplit[1]);

                    if (pfunction == null) {
                        throw new IllegalArgumentException();
                    }

                    powner.pfunctions.put(keysplit[3], pfunction);
                } else if ("static".equals(keysplit[1])) {
                    PField pstatik = porigin.pstatics.get(propsplit[1]);

                    if (pstatik == null) {
                        throw new IllegalArgumentException();
                    }

                    powner.pstatics.put(keysplit[3], pstatik);
                } else {
                    throw new IllegalArgumentException();
                }
            }
        }
    }

    final Type getATypeFromPClass(final String ptype) {
        int index = ptype.indexOf('[');
        String type = ptype;
        String dimensions = "";

        if (index != -1) {
            type = ptype.substring(0, index);
            final int length = ptype.length();

            while (index < length) {
                if (ptype.charAt(index) == '[' && ++index < length && ptype.charAt(index++) == ']') {
                    dimensions += "[";
                } else {
                    throw new IllegalArgumentException();
                }
            }
        }

        PClass pclass = pclasses.get(type);

        if (pclass == null) {
            throw new IllegalArgumentException();
        }

        final String descriptor = dimensions + pclass.atype.getDescriptor();
        return Type.getType(descriptor);
    }

    final PClass getPClass(final Type atype) {
        return aclasses.get(atype);
    }

    final PMethod getPTransform(final PCast pcast) {
        return ptransforms.get(pcast);
    }
}
