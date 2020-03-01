package chocopy.common;

import java.io.BufferedReader;
import java.util.stream.Collectors;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;


/** Utility functions for general use. */
public class Utils {

    /**
     * Return resource file FILENAME's contents as a string.  FILENAME
     * can refer to a file within the class hierarchy, so that a text
     * resource in file resource.txt in the chocopy.common.codegen
     * package, for example, could be referred to with FILENAME
     * chocopy/common/codegen/resource.txt.
     *
     * Credit: Lucio Paiva.
     */
    public static String getResourceFileAsString(String fileName) {
        InputStream is =
            Utils.class.getClassLoader().getResourceAsStream(fileName);
        if (is != null) {
            BufferedReader reader =
                new BufferedReader
                (new InputStreamReader(is, Charset.forName("UTF-8")));
            return reader.lines().collect
                (Collectors.joining(System.lineSeparator()));
        }
        return null;
    }

    /** Return an exception signalling a fatal error having a message
     *  formed from MSGFORMAT and ARGS, as for String.format. */
    public static Error fatal(String msgFormat, Object... args) {
        return new Error(String.format(msgFormat, args));
    }

    /** Return the string S padded with FILL to TOLEN characters.  Padding
     *  is on the left if PADONLEFT, and otherwise on the right. If S is
     *  already at least TOLEN characters, returns S. */
    public static String pad(String s, Character fill, int toLen,
                             boolean padOnLeft) {
        StringBuilder result = new StringBuilder(toLen);
        if (!padOnLeft) {
            result.append(s);
        }
        for (int n = s.length(); n < toLen; n += 1) {
            result.append(fill);
        }
        if (padOnLeft) {
            result.append(s);
        }
        return result.toString();
    }
}
