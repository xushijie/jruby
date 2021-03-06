/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.runtime.core;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.jcodings.Encoding;
import org.jcodings.specific.UTF8Encoding;
import org.jruby.runtime.Helpers;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.util.ByteList;
import org.jruby.util.StringSupport;

/**
 * Represents the Ruby {@code String} class.
 */
public class RubyString extends RubyBasicObject {

    /**
     * The class from which we create the object that is {@code String}. A subclass of
     * {@link RubyClass} so that we can override {@link RubyClass#newInstance} and allocate a
     * {@link RubyString} rather than a normal {@link RubyBasicObject}.
     */
    public static class RubyStringClass extends RubyClass {

        public RubyStringClass(RubyContext context, RubyModule lexicalParent, RubyClass superclass, String name) {
            super(context, lexicalParent, superclass, name);
        }

        @Override
        public RubyBasicObject newInstance(RubyNode currentNode) {
            return new RubyString(this, new ByteList());
        }

    }

    private ByteList bytes;

    public RubyString(RubyClass stringClass, ByteList bytes) {
        super(stringClass);
        this.bytes = bytes;
    }

    public static RubyString fromJavaString(RubyClass stringClass, String string) {
        return new RubyString(stringClass, new ByteList(org.jruby.RubyEncoding.encodeUTF8(string), UTF8Encoding.INSTANCE, false));
    }

    public static RubyString fromByteList(RubyClass stringClass, ByteList bytes) {
        return new RubyString(stringClass, bytes);
    }

    public void set(ByteList bytes) {
        this.bytes = bytes;
    }

    public void forceEncoding(Encoding encoding) {
        this.bytes.setEncoding(encoding);
    }

    public ByteList getBytes() {
        return bytes;
    }

    public static String ljust(String string, int length, String padding) {
        final StringBuilder builder = new StringBuilder();

        builder.append(string);

        int n = 0;

        while (builder.length() < length) {
            builder.append(padding.charAt(n));

            n++;

            if (n == padding.length()) {
                n = 0;
            }
        }

        return builder.toString();
    }

    public static String rjust(String string, int length, String padding) {
        final StringBuilder builder = new StringBuilder();

        int n = 0;

        while (builder.length() + string.length() < length) {
            builder.append(padding.charAt(n));

            n++;

            if (n == padding.length()) {
                n = 0;
            }
        }

        builder.append(string);

        return builder.toString();
    }

    public int count(RubyString[] otherStrings) {
        if (bytes.getRealSize() == 0) {
            return 0;
        }

        RubyString otherStr = otherStrings[0];
        Encoding enc = otherStr.getBytes().getEncoding();

        final boolean[]table = new boolean[StringSupport.TRANS_SIZE + 1];
        StringSupport.TrTables tables = StringSupport.trSetupTable(otherStr.getBytes(), getContext().getRuntime(), table, null, true, enc);
        for (int i = 1; i < otherStrings.length; i++) {
            otherStr = otherStrings[i];

            // TODO (nirvdrum Dec. 19, 2014): This method should be encoding aware and check that the strings have compatible encodings.  See non-Truffle JRuby for a more complete solution.
            //enc = checkEncoding(otherStr);
            tables = StringSupport.trSetupTable(otherStr.getBytes(), getContext().getRuntime(), table, tables, false, enc);
        }

        return StringSupport.countCommon19(getBytes(), getContext().getRuntime(), table, tables, enc);
    }

    public RubyString dump() {
        ByteList outputBytes = StringSupport.dumpCommon(getContext().getRuntime(), bytes);

        final RubyString result = getContext().makeString(outputBytes);

        return result;
    }

    @Override
    public boolean equals(Object other) {
        RubyNode.notDesignedForCompilation();

        if (other == this) {
            return true;
        }

        if (other instanceof String || other instanceof RubyString) {
            return toString().equals(other.toString());
        }

        return false;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        RubyNode.notDesignedForCompilation();

        return Helpers.decodeByteList(getContext().getRuntime(), bytes);
    }

    @Override
    public int hashCode() {
        RubyNode.notDesignedForCompilation();

        return bytes.hashCode();
    }

    public int normaliseIndex(int index) {
        return RubyArray.normaliseIndex(bytes.length(), index);
    }

    public int clampExclusiveIndex(int index) {
        return RubyArray.clampExclusiveIndex(bytes.length(), index);
    }

}
