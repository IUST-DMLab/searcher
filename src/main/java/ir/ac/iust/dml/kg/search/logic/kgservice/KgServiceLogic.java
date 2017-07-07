package ir.ac.iust.dml.kg.search.logic.kgservice;

import ir.ac.iust.dml.kg.raw.utils.ConfigReader;
import ir.ac.iust.dml.kg.raw.utils.PrefixService;
import ir.ac.iust.dml.kg.search.logic.kgservice.data.*;
import ir.ac.iust.dml.kg.virtuoso.connector.VirtuosoConnector;
import ir.ac.iust.dml.kg.virtuoso.connector.data.VirtuosoTriple;
import ir.ac.iust.dml.kg.virtuoso.connector.data.VirtuosoTripleType;

import java.util.List;

public class KgServiceLogic {

    private final VirtuosoConnector connector;

    private final String SUBCLASS = PrefixService.INSTANCE.prefixToUri(PrefixService.INSTANCE.getSUB_CLASS_OF());
    private final String LABEL = PrefixService.INSTANCE.prefixToUri(PrefixService.INSTANCE.getLABEL_URL());
    private final String DOMAIN = PrefixService.INSTANCE.prefixToUri(PrefixService.INSTANCE.getPROPERTY_DOMAIN_URL());
    private final String INSTANCE_OF = PrefixService.INSTANCE.prefixToUri(PrefixService.INSTANCE.getINSTANCE_OF_URL());
    private final String TYPE = PrefixService.INSTANCE.prefixToUri(PrefixService.INSTANCE.getTYPE_URL());

    public KgServiceLogic() {
        System.err.println("Loading KgServiceLogic ...");
        long t1 = System.currentTimeMillis();
        final String virtuosoServer = ConfigReader.INSTANCE.getString("virtuoso.address", "localhost:1111");
        final String virtuosoUser = ConfigReader.INSTANCE.getString("virtuoso.user", "dba");
        final String virtuosoPass = ConfigReader.INSTANCE.getString("virtuoso.password", "fkgVIRTUOSO2017");
        connector = new VirtuosoConnector("http://fkg.iust.ac.ir/new", virtuosoServer, virtuosoUser, virtuosoPass);
        System.err.printf("KgServiceLogic loaded in %,d ms", (System.currentTimeMillis() - t1));
    }

    private String getLabel(String url) {
        if (url == null) return null;
        final List<VirtuosoTriple> parent = connector.getTriples(url, LABEL);
        if (parent == null || parent.isEmpty() || parent.get(0).getObject() == null) return null;
        final Object label = parent.get(0).getObject().getValue();
        if (label == null) return null;
        return label.toString();
    }

    public ParentNode getParent(String childUrl) {
        final List<VirtuosoTriple> parent = connector.getTriples(childUrl, SUBCLASS);
        if (parent == null || parent.isEmpty() || parent.get(0).getObject() == null) return null;
        final String parentUrl = parent.get(0).getObject().getValue().toString();
        return new ParentNode(parentUrl, getLabel(parentUrl));
    }

    public ChildNodes getChildren(String parentUrl) {
        final List<VirtuosoTriple> children = connector.getTriplesOfObject(SUBCLASS, parentUrl);
        if (children == null || children.isEmpty()) return null;
        final ChildNodes result = new ChildNodes();
        for (VirtuosoTriple triple : children) {
            if (triple.getSource() == null) continue;
            result.getChildNodes().add(new ChildNode(triple.getSource(), getLabel(triple.getSource())));
        }
        return result;
    }

    public ClassInfo getClassInfo(String url) {
        final ClassInfo classData = new ClassInfo(getLabel(url));
        final List<VirtuosoTriple> triples = connector.getTriplesOfObject(DOMAIN, url);
        if (triples == null || triples.isEmpty()) return null;
        for (VirtuosoTriple triple : triples) {
            if (triple.getPredicate() == null || triple.getSource() == null) continue;
            classData.getProperties().add(new PropertyInfo(triple.getSource(),
                    getLabel(triple.getSource()), false));
        }
        return classData;
    }

    public EntityData getEntityInfo(String url) {
        final EntityData entityData = new EntityData(getLabel(url));
        final List<VirtuosoTriple> triples = connector.getTriplesOfSubject(url);
        if (triples == null || triples.isEmpty()) return null;
        for (VirtuosoTriple triple : triples) {
            if (triple.getPredicate() == null || triple.getObject() == null || triple.getObject() == null) continue;
            final String predicateLabel = getLabel(triple.getPredicate());
            if (predicateLabel == null) continue;
            final PropertyValue value = new PropertyValue();
            if (triple.getObject().getType() == VirtuosoTripleType.Resource)
                value.setUrl(triple.getObject().getValue().toString());
            else value.setContent(triple.getObject().getValue().toString());
            final PropertyData data = entityData.getPropertyMap().get(triple.getPredicate());
            if(data != null) data.getPropValue().add(value);
            else entityData.getPropertyMap().put(triple.getPredicate(),
                    new PropertyData(triple.getPredicate(), predicateLabel, value));
        }
        return entityData;
    }

    public Entities getEntitiesOfClass(String classUrl) {
        final Entities entities = new Entities();
        final List<VirtuosoTriple> triples = connector.getTriplesOfObject(TYPE, classUrl);
        if (triples == null) return null;
        for (VirtuosoTriple triple : triples) {
            if (triple.getPredicate() == null || triple.getSource() == null) continue;
            final String label = getLabel(triple.getSource());
            if (label == null) continue;
            entities.getResult().add(new EntityInfo(triple.getSource(), label));
        }
        return entities;
    }

}
