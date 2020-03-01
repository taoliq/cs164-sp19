package chocopy.common.analysis.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A symbol type represents the static types of symbols and expressions
 * during type-checking.
 *
 * Symbols such as variables and attributes will typically
 * map to a {@link ValueType}.
 *
 * Symbols such as classes will typically map to a more complex SymbolType.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.PROPERTY,
              property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(FuncType.class),
        @JsonSubTypes.Type(ClassValueType.class),
        @JsonSubTypes.Type(ListValueType.class)})
public abstract class SymbolType {

    /** The type object. */
    public static final ClassValueType OBJECT_TYPE =
        new ClassValueType("object");
    /** The type int. */
    public static final ClassValueType INT_TYPE = new ClassValueType("int");
    /** The type str. */
    public static final ClassValueType STR_TYPE = new ClassValueType("str");
    /** The type bool. */
    public static final ClassValueType BOOL_TYPE = new ClassValueType("bool");

    /** The type of None. */
    public static final ClassValueType NONE_TYPE =
        new ClassValueType("<None>");
    /** The type of []. */
    public static final ClassValueType EMPTY_TYPE =
        new ClassValueType("<Empty>");


    /** Returns the name of the class, if this is a class type,
     *  Otherwise null. */
    public String className() {
        return null;
    }

    /** Return true iff this is a type that does not include the value None.
     */
    @JsonIgnore
    public boolean isSpecialType() {
        return equals(INT_TYPE) || equals(BOOL_TYPE) || equals(STR_TYPE);
    }

    @JsonIgnore
    public boolean isListType() {
        return false;
    }

    @JsonIgnore
    public boolean isFuncType() {
        return false;
    }

    /** Return true iff this type represents a kind of assignable value. */
    @JsonIgnore
    public boolean isValueType() {
        return false;
    }

    /** For list types, return the type of the elements; otherwise null. */
    @JsonIgnore
    public ValueType elementType() {
        return null;
    }

}
