package chocopy.common;

import java.io.BufferedReader;
import java.util.stream.Collectors;
import java.io.InputStream;
import java.io.InputStreamReader;


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
                new BufferedReader(new InputStreamReader(is));
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

}
