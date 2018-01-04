package ir.ac.iust.dml.kg.search.logic.recommendation;

import com.google.gson.Gson;
import ir.ac.iust.dml.kg.raw.utils.ConfigReader;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class RecommendationLoader {
    public static Map<String, Recommendation[]> read() throws SQLException {
        final  Map<String, Recommendation[]> recommendations = new HashMap<>();

        //avoid loading  recommendations on local runs
        if(Files.exists(Paths.get("noRecom")))
            return recommendations;

        final Connection con = DriverManager.getConnection(
            "jdbc:mysql://" +
                ConfigReader.INSTANCE.getString("recommendation.sql.address", "localhost:3306") +
                "/recommendations?useUnicode=true" +
                "&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false" +
                "&serverTimezone=UTC&useSSL=false",
            ConfigReader.INSTANCE.getString("recommendation.sql.user", "recommendation_user"),
            ConfigReader.INSTANCE.getString("recommendation.sql.password", "recommendation_pass"));
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
                        for (Recommendation rec : recs)
                            rec.deduplicate();
                        recommendations.put(uri, recs);
                    }
                }catch(Exception e){
                    System.err.println("Error while loading recommendations" + i);
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
