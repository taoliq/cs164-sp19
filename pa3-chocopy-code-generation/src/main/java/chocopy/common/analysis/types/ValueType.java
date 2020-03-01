package chocopy.common.analysis.types;

import chocopy.common.astnodes.ClassType;
import chocopy.common.astnodes.ListType;
import chocopy.common.astnodes.TypeAnnotation;

/**
 * A ValueType references types that are assigned to variables and
 * expressions.
 *
 * In particular, ValueType can be a {@link ClassValueType} (e.g. "int") or
 * a {@link ListValueType} (e.g. "[int]").
 */

public abstract class ValueType extends SymbolType {

    /** Returns the type corresponding to ANNOTATION. */
    public static ValueType annotationToValueType(TypeAnnotation annotation) {
        if (annotation instanceof ClassType) {
            return new ClassValueType((ClassType) annotation);
        } else {
            assert annotation instanceof ListType;
            return new ListValueType((ListType) annotation);
        }
    }

    @Override
    public boolean isValueType() {
        return true;
    }

}
