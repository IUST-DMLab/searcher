package ir.ac.iust.dml.kg.search.logic;

/**
 * Created by ali on 4/16/17.
 */
public class Util {
    public static boolean textIsPersian(String s) {
        for (int i = 0; i < Character.codePointCount(s, 0, s.length()); i++) {
            int c = s.codePointAt(i);
            if (c >= 0x0600 && c <= 0x06FF || c == 0xFB8A || c == 0x067E || c == 0x0686 || c == 0x06AF)
                return true;
        }
        return false;
    }
}
