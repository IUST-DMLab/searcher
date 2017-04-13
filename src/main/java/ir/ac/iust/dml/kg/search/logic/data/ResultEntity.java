package ir.ac.iust.dml.kg.search.logic.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResultEntity {
    private String link;
    private String title;
    private String subtitle;
    private String description;
    private List<String> photoUrls = new ArrayList<String>();
    private Map<String, DataValues> keyValues = new HashMap<String, DataValues>();

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(List<String> photoUrls) {
        this.photoUrls = photoUrls;
    }

    public Map<String, DataValues> getKeyValues() {
        return keyValues;
    }

    public void setKeyValues(Map<String, DataValues> keyValues) {
        this.keyValues = keyValues;
    }
}
