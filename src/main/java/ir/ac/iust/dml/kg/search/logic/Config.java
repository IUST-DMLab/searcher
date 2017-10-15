package ir.ac.iust.dml.kg.search.logic;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class Config {
    public static Properties prop = loadConfig();

    private static Properties loadConfig(){
        Properties prop = new Properties();
        try {
            File configFile = new File("config.properties");
            FileReader reader = new FileReader(configFile);
            Properties props = new Properties();
            // load the properties file:
            props.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return prop;
    }
}
