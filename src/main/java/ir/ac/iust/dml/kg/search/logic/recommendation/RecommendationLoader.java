package ir.ac.iust.dml.kg.search.logic.recommendation;

import com.google.gson.Gson;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class RecommendationLoader {
    public static Map<String, Recommendation[]> read() throws SQLException {
        final  Map<String, Recommendation[]> recommendations = new HashMap<>();
        final Connection con = DriverManager.getConnection(
                "jdbc:mysql://dmls.iust.ac.ir:3306/recommendations?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false", "paydar", "paydar");
        try {
            for(int i=2; i<=10; i++) {
                try {
                    Statement stmt = con.createStatement();
                    ResultSet rs = stmt.executeQuery("select uri, results  from recommendations" + i);
                    Gson g = new Gson();
                    while (rs.next()) {
                        final String uri = rs.getString(1);
                        final String results = rs.getString(2);
                        final Recommendation[] recs = g.fromJson(results, Recommendation[].class);
                        recommendations.put(uri, recs);
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        } finally {
            con.close();
        }
        return recommendations;
    }

    public static void main(String[] args) throws SQLException {
        new RecommendationLoader().read();
    }
}
