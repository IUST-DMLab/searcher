import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class Test {
    public static void main(String[] args) {
        List<String> l = new ArrayList<>();
        l.add("salam");
        l.add("Chetori");
        System.out.println(l.stream().reduce("",(a,b) -> a + "," + b));

    }
}
