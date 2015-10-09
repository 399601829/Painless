package painless;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static painless.Adapter.*;
import static painless.Default.*;
import static painless.Definition.*;

class Caster {
    private abstract static class Segment {
        abstract Type promote(final Type from0, final Type from1);
    }

    private static class SameTypeSegment extends Segment {
        @Override
        Type promote(final Type from0, final Type from1) {
            if (from1 != null && from0.equals(from1)) {
                return from0;
            }

            return null;
        }
    }

    private static class AnyTypeSegment extends Segment {
        private final Caster caster;
        private final Type to;

        AnyTypeSegment(final Caster caster, final Type to) {
            this.caster = caster;
            this.to = to;
        }

        @Override
        Type promote(final Type from0, final Type from1) {
            final boolean eq0 = from0.equals(to);
            final boolean eq1 = from1 != null && from1.equals(to);

            if (eq0 && (from1 == null || eq1)) {
                return to;
            }

            if (eq0 || eq1) {
                try {
                    caster.getLegalCast(eq0 ? from1 : from0, to, false);

                    return to;
                } catch (ClassCastException exception) {
                    // Do nothing.
                }
            }

            return null;
        }
    }

    private static class ToTypeSegment extends Segment {
        private final Caster caster;
        private final Type to;

        ToTypeSegment(final Caster caster, final Type to) {
            this.caster = caster;
            this.to = to;
        }

        @Override
        Type promote(final Type from0, final Type from1) {
            final boolean eq0 = from0.equals(to);
            final boolean eq1 = from1 == null || from1.equals(to);

            if (eq0 && eq1) {
                return to;
            }

            boolean castable = true;

            if (!eq0) {
                try {
                    caster.getLegalCast(from0, to, false);
                } catch (ClassCastException exception) {
                    castable = false;
                }
            }

            if (!eq1) {
                try {
                    caster.getLegalCast(from1, to, false);
                } catch (ClassCastException exception) {
                    castable = false;
                }
            }

            if (castable) {
                return to;
            }

            return null;
        }
    }

    private static class AnyNumericSegment extends Segment {
        private final Caster caster;
        private final boolean decimal;

        AnyNumericSegment(final Caster caster, final boolean decimal) {
            this.caster = caster;
            this.decimal = decimal;
        }

        @Override
        Type promote(final Type from0, final Type from1) {
            if (from0.metadata.numeric || from1 != null && from1.metadata.numeric) {
                try {
                    return caster.getNumericPromotion(from0, from1, decimal);
                } catch (ClassCastException exception) {
                    // Do nothing.
                }
            }

            return null;
        }
    }

    private static class ToNumericSegment extends Segment {
        private final Caster caster;
        private final boolean decimal;

        ToNumericSegment(final Caster caster, final boolean decimal) {
            this.caster = caster;
            this.decimal = decimal;
        }

        @Override
        Type promote(final Type from0, final Type from1) {
            try {
                return caster.getNumericPromotion(from0, from1, decimal);
            } catch (ClassCastException exception) {
                return null;
            }
        }
    }

    private static class ToSuperClassSegment extends Segment {
        final Definition definition;

        ToSuperClassSegment(final Definition definition) {
            this.definition = definition;
        }

        @Override
        Type promote(final Type from0, final Type from1) {
            if (from0.equals(from1)) {
                return from0;
            }

            final Cast cast0 = new Cast(from0, from1);
            final Cast cast1 = new Cast(from1, from0);

            if (definition.upcasts.contains(cast0)) {
                return from1;
            }

            if (definition.upcasts.contains(cast1)) {
                return from0;
            }

            return null;
        }
    }

    private static class ToSubClassSegment extends Segment {
        final Definition definition;
        final Standard standard;

        ToSubClassSegment(final Definition definition, final Standard standard) {
            this.definition = definition;
            this.standard = standard;
        }

        @Override
        Type promote(final Type from0, final Type from1) {
            if (from0.equals(from1)) {
                return from0;
            }

            if (from0.clazz.equals(from1.clazz)) {
                Cast cast0 = null;
                Cast cast1 = null;

                if (from0.struct.generic && !from1.struct.generic) {
                    cast0 = new Cast(from0, from1);
                    cast1 = new Cast(from1, from0);
                } else if (!from0.struct.generic && from1.struct.generic) {
                    cast0 = new Cast(from1, from0);
                    cast1 = new Cast(from0, from1);
                }

                if (cast0 != null && cast1 != null) {
                    if (definition.implicits.containsKey(cast0)) {
                        return cast0.to;
                    }

                    if (definition.implicits.containsKey(cast1)) {
                        return cast1.to;
                    }
                }

                return standard.objectType;
            }

            try {
                from0.clazz.asSubclass(from1.clazz);

                return from1;
            } catch (ClassCastException cce0) {
                // Do nothing.
            }

            try {
                from1.clazz.asSubclass(from1.clazz);

                return from0;
            } catch (ClassCastException cce0) {
                // Do nothing.
            }

            if (from0.metadata.object && from1.metadata.object) {
                return standard.objectType;
            }

            return null;
        }
    }

    static class Promotion {
        private final List<Segment> segments;

        Promotion(final List<Segment> segments) {
            this.segments = Collections.unmodifiableList(segments);
        }
    }

    private final Definition definition;
    private final Standard standard;

    final Promotion equality;
    final Promotion decimal;
    final Promotion numeric;
    final Promotion shortcut;

    Caster(final Definition definition, final Standard standard) {
        this.definition = definition;
        this.standard = standard;

        List<Segment> segments = new ArrayList<>();
        segments.add(new SameTypeSegment());
        segments.add(new AnyTypeSegment(this, standard.boolType));
        segments.add(new AnyNumericSegment(this, true));
        segments.add(new ToSuperClassSegment(definition));
        segments.add(new ToSubClassSegment(definition, standard));
        equality = new Promotion(segments);

        segments = new ArrayList<>();
        segments.add(new ToNumericSegment(this, true));
        decimal = new Promotion(segments);

        segments = new ArrayList<>();
        segments.add(new ToNumericSegment(this, false));
        numeric = new Promotion(segments);

        segments = new ArrayList<>();
        segments.add(new ToTypeSegment(this, standard.intType));
        segments.add(new ToTypeSegment(this, standard.objectType));
        shortcut = new Promotion(segments);
    }

    void markCast(final ExpressionMetadata emd) {
        if (emd.from == null) {
            throw new IllegalStateException(); // TODO: message
        }

        if (emd.to != null) {
            emd.cast = getLegalCast(emd.from, emd.to, emd.explicit);

            if (emd.preConst != null && emd.to.metadata.constant) {
                emd.postConst = constCast(emd.preConst, emd.cast);
            }
        } else if (emd.promotion == null) {
            throw new IllegalStateException(); // TODO: message
        }
    }

    Cast getLegalCast(final Type from, final Type to, /*final*/ boolean force) {
        force = true; // TEST

        final Cast cast = new Cast(from, to);

        if (from.equals(to)) {
            return cast;
        }

        final Transform explicit = definition.explicits.get(cast);

        if (force && explicit != null) {
            return explicit;
        }

        final Transform implicit = definition.implicits.get(cast);

        if (implicit != null) {
            return implicit;
        }

        if (definition.upcasts.contains(cast)) {
            return cast;
        }

        if (from.metadata.numeric && to.metadata.numeric && (force || definition.numerics.contains(cast))) {
            return cast;
        }

        try {
            from.clazz.asSubclass(to.clazz);

            return cast;
        } catch (ClassCastException cce0) {
            try {
                if (force) {
                    to.clazz.asSubclass(from.clazz);

                    return cast;
                } else {
                    throw new ClassCastException(); // TODO: message
                }
            } catch (ClassCastException cce1) {
                throw new ClassCastException(); // TODO: message
            }
        }
    }

    Object constCast(final Object constant, final Cast cast) {
        if (cast instanceof Transform) {
            final Transform transform = (Transform)cast;
            return invokeTransform(transform, constant);
        } else {
            final TypeMetadata fromTMD = cast.from.metadata;
            final TypeMetadata toTMD = cast.to.metadata;

            if (fromTMD == toTMD) {
                return constant;
            } else if (fromTMD.numeric && toTMD.numeric) {
                Number number;

                if (fromTMD == TypeMetadata.CHAR) {
                    number = (int)(char)constant;
                } else {
                    number = (Number)constant;
                }

                switch (toTMD) {
                    case BYTE:   return number.byteValue();
                    case SHORT:  return number.shortValue();
                    case CHAR:   return (char)number.intValue();
                    case INT:    return number.intValue();
                    case LONG:   return number.longValue();
                    case FLOAT:  return number.floatValue();
                    case DOUBLE: return number.doubleValue();
                    default:
                        throw new IllegalStateException();
                }
            } else {
                throw new IllegalStateException(); // TODO: message
            }
        }
    }

    private Object invokeTransform(final Transform transform, final Object object) {
        final Method method = transform.method;
        final java.lang.reflect.Method jmethod = method.method;
        final int modifiers = jmethod.getModifiers();

        try {
            if (java.lang.reflect.Modifier.isStatic(modifiers)) {
                return jmethod.invoke(null, object);
            } else {
                return jmethod.invoke(object);
            }
        } catch (IllegalAccessException | IllegalArgumentException |
                java.lang.reflect.InvocationTargetException | NullPointerException |
                ExceptionInInitializerError exception) {
            throw new IllegalArgumentException(); // TODO: message
        }
    }

    Type getTypePromotion(final Type from0, final Type from1, final Promotion promotion) {
        for (final Segment segment : promotion.segments) {
            final Type type = segment.promote(from0, from1);

            if (type != null) {
                return type;
            }
        }

        throw new ClassCastException(); // TODO: message
    }

    Type getNumericPromotion(final Type from0, final Type from1, boolean decimal) {
        if (!from0.metadata.numeric || from1 != null && !from1.metadata.numeric) { // TEST
            return standard.doubleType; // TEST
        } // TEST

        final Deque<Type> upcast = new ArrayDeque<>();
        final Deque<Type> downcast = new ArrayDeque<>();

        if (decimal) {
            upcast.push(standard.doubleType);
            upcast.push(standard.floatType);
        } else {
            downcast.push(standard.doubleType);
            downcast.push(standard.floatType);
        }

        upcast.push(standard.longType);
        upcast.push(standard.intType);

        while (!upcast.isEmpty()) {
            final Type to = upcast.pop();
            final Cast cast0 = new Cast(from0, to);

            if (from0.metadata.numeric && from0.metadata != to.metadata &&
                    !definition.numerics.contains(cast0))                            continue;
            if (upcast.contains(from0))                                              continue;
            if (downcast.contains(from0) && !definition.numerics.contains(cast0) &&
                    !definition.implicits.containsKey(cast0))                        continue;
            if (!from0.metadata.numeric && !definition.implicits.containsKey(cast0)) continue;

            if (from1 != null) {
                final Cast cast1 = new Cast(from1, to);

                if (from1.metadata.numeric && from1.metadata != to.metadata &&
                        !definition.numerics.contains(cast1))                            continue;
                if (upcast.contains(from1))                                              continue;
                if (downcast.contains(from1) && !definition.numerics.contains(cast1) &&
                        !definition.implicits.containsKey(cast1))                        continue;
                if (!from1.metadata.numeric && !definition.implicits.containsKey(cast1)) continue;
            }

            return to;
        }

        throw new ClassCastException(); // TODO: message
    }

    void checkWriteCast(final MethodVisitor visitor, final ExpressionMetadata metadata) {
        checkWriteCast(visitor, metadata.cast);
    }

    void checkWriteCast(final MethodVisitor visitor, final Cast cast) {
        if (cast instanceof Transform) {
            writeTransform(visitor, (Transform)cast);
        } else if (cast != null) {
            writeCast(visitor, cast);
        } else {
            throw new IllegalStateException(); // TODO: message
        }
    }

    void writeCast(final MethodVisitor visitor, final Cast cast) {
        final Type from = cast.from;
        final Type to = cast.to;

        if (from.equals(to)) {
            return;
        }

        if (from.metadata.numeric && to.metadata.numeric) {
            switch (from.metadata) {
                case BYTE:
                    switch (to.metadata) {
                        case SHORT:  visitor.visitInsn(Opcodes.I2S); break;
                        case CHAR:   visitor.visitInsn(Opcodes.I2C); break;
                        case LONG:   visitor.visitInsn(Opcodes.I2L); break;
                        case FLOAT:  visitor.visitInsn(Opcodes.I2F); break;
                        case DOUBLE: visitor.visitInsn(Opcodes.I2D); break;
                    }
                    break;
                case SHORT:
                    switch (to.metadata) {
                        case BYTE:   visitor.visitInsn(Opcodes.I2B); break;
                        case CHAR:   visitor.visitInsn(Opcodes.I2C); break;
                        case LONG:   visitor.visitInsn(Opcodes.I2L); break;
                        case FLOAT:  visitor.visitInsn(Opcodes.I2F); break;
                        case DOUBLE: visitor.visitInsn(Opcodes.I2D); break;
                    }
                    break;
                case CHAR:
                    switch (to.metadata) {
                        case BYTE:   visitor.visitInsn(Opcodes.I2B); break;
                        case SHORT:  visitor.visitInsn(Opcodes.I2S); break;
                        case LONG:   visitor.visitInsn(Opcodes.I2L); break;
                        case FLOAT:  visitor.visitInsn(Opcodes.I2F); break;
                        case DOUBLE: visitor.visitInsn(Opcodes.I2D); break;
                    }
                    break;
                case INT:
                    switch (to.metadata) {
                        case BYTE:   visitor.visitInsn(Opcodes.I2B); break;
                        case SHORT:  visitor.visitInsn(Opcodes.I2S); break;
                        case CHAR:   visitor.visitInsn(Opcodes.I2C); break;
                        case LONG:   visitor.visitInsn(Opcodes.I2L); break;
                        case FLOAT:  visitor.visitInsn(Opcodes.I2F); break;
                        case DOUBLE: visitor.visitInsn(Opcodes.I2D); break;
                    }
                    break;
                case LONG:
                    switch (to.metadata) {
                        case BYTE:   visitor.visitInsn(Opcodes.L2I); visitor.visitInsn(Opcodes.I2B); break;
                        case SHORT:  visitor.visitInsn(Opcodes.L2I); visitor.visitInsn(Opcodes.I2S); break;
                        case CHAR:   visitor.visitInsn(Opcodes.L2I); visitor.visitInsn(Opcodes.I2C); break;
                        case INT:    visitor.visitInsn(Opcodes.L2I); break;
                        case FLOAT:  visitor.visitInsn(Opcodes.L2F); break;
                        case DOUBLE: visitor.visitInsn(Opcodes.L2D); break;
                    }
                    break;
                case FLOAT:
                    switch (to.metadata) {
                        case BYTE:   visitor.visitInsn(Opcodes.F2I); visitor.visitInsn(Opcodes.I2B); break;
                        case SHORT:  visitor.visitInsn(Opcodes.F2I); visitor.visitInsn(Opcodes.I2S); break;
                        case CHAR:   visitor.visitInsn(Opcodes.F2I); visitor.visitInsn(Opcodes.I2C); break;
                        case INT:    visitor.visitInsn(Opcodes.F2I); break;
                        case LONG:   visitor.visitInsn(Opcodes.F2L); break;
                        case DOUBLE: visitor.visitInsn(Opcodes.F2D); break;
                    }
                    break;
                case DOUBLE:
                    switch (to.metadata) {
                        case BYTE:  visitor.visitInsn(Opcodes.D2I); visitor.visitInsn(Opcodes.I2B); break;
                        case SHORT: visitor.visitInsn(Opcodes.D2I); visitor.visitInsn(Opcodes.I2S); break;
                        case CHAR:  visitor.visitInsn(Opcodes.D2I); visitor.visitInsn(Opcodes.I2C); break;
                        case INT:   visitor.visitInsn(Opcodes.D2I); break;
                        case LONG:  visitor.visitInsn(Opcodes.D2L); break;
                        case FLOAT: visitor.visitInsn(Opcodes.D2F); break;
                    }
                    break;
            }
        } else {
            try {
                from.clazz.asSubclass(to.clazz);
            } catch (ClassCastException exception) {
                visitor.visitTypeInsn(Opcodes.CHECKCAST, to.internal);
            }
        }
    }

    void writeTransform(final MethodVisitor visitor, final Transform transform) {
        final Class clazz = transform.method.owner.clazz;
        final java.lang.reflect.Method method = transform.method.method;

        final String name = method.getName();
        final String internal = transform.method.owner.internal;
        final String descriptor = transform.method.descriptor;

        final Type upcast = transform.upcast;
        final Type downcast = transform.downcast;

        if (upcast != null) {
            visitor.visitTypeInsn(Opcodes.CHECKCAST, upcast.internal);
        }

        if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, internal, name, descriptor, false);
        } else if (java.lang.reflect.Modifier.isInterface(clazz.getModifiers())) {
            visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, internal, name, descriptor, true);
        } else {
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, name, descriptor, false);
        }

        if (downcast != null) {
            visitor.visitTypeInsn(Opcodes.CHECKCAST, downcast.internal);
        }
    }
}
