package ir.ac.iust.dml.kg.search.logic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

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


    /**
     * Used for removing duplicates by property. see https://stackoverflow.com/q/23699371/2571490
     * @param keyExtractor
     * @param <T>
     * @return
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object,Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }


    public static String iriToLabel(String iri)
    {
        String[] splits = iri.split("/");
        return splits[splits.length-1].replace('_',' ');
    }

    public static void main(String[] args) {
        String s = "http://fkg.iust.ac.ir/resource/سلام_(آلبوم_سامی_یوسف)";
        System.out.println(iriToLabel(s));
    }
}
