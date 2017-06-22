package ir.ac.iust.dml.kg.search.logic.kgservice.data;

import java.util.ArrayList;
import java.util.List;

public class EntityData {
    private String label;
    private List<PropertyData> properties = new ArrayList<>();

    public EntityData() {
    }

    public EntityData(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<PropertyData> getProperties() {
        return properties;
    }

    public void setProperties(List<PropertyData> properties) {
        this.properties = properties;
    }
}
