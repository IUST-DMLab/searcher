package ir.ac.iust.dml.kg.search.logic.kgservice.data;

public class PropertyData {
    private String propUrl;
    private String propLabel;
    private PropertyValue propValue;

    public PropertyData() {
    }

    public PropertyData(String propUrl, String propLabel, PropertyValue propValue) {
        this.propUrl = propUrl;
        this.propLabel = propLabel;
        this.propValue = propValue;
    }

    public String getPropUrl() {
        return propUrl;
    }

    public void setPropUrl(String propUrl) {
        this.propUrl = propUrl;
    }

    public String getPropLabel() {
        return propLabel;
    }

    public void setPropLabel(String propLabel) {
        this.propLabel = propLabel;
    }

    public PropertyValue getPropValue() {
        return propValue;
    }

    public void setPropValue(PropertyValue propValue) {
        this.propValue = propValue;
    }
}
