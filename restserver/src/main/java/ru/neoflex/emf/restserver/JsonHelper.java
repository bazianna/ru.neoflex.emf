package ru.neoflex.emf.restserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
//import ru.neoflex.emf.base.HbResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public abstract class JsonHelper {
    JsonMapper mapper = new JsonMapper();

    public ObjectNode toJson(EObject eObject) {
        return toJson(eObject, (EClass) null);
    }

    public ObjectNode toJson(EObject eObject, EClass eClass) {
        ObjectNode result = mapper.createObjectNode();
        if (!eObject.eClass().equals(eClass)) {
            result.put("eClass", EcoreUtil.getURI(eObject.eClass()).toString());
        }
        result.put("_id", eObject.eResource().getURIFragment(eObject));
        for (EStructuralFeature sf: eObject.eClass().getEAllStructuralFeatures()) {
            if (!sf.isDerived() && !sf.isTransient() && eObject.eIsSet(sf)) {
                JsonNode valueNode = toJson(eObject, sf);
                if (valueNode != null) {
                    result.set(sf.getName(), valueNode);
                }
            }
        }
        return result;
    }

    private JsonNode toJson(EObject eObject, EStructuralFeature sf) {
        if (sf instanceof EAttribute) return toJson(eObject, (EAttribute) sf);
        if (sf instanceof EReference) return toJson(eObject, (EReference) sf);
        return null;
    }

    private JsonNode toJson(EObject eObject, EAttribute eAttribute) {
        if (!eAttribute.isMany()) {
            Object value = eObject.eGet(eAttribute);
            return toJson(eAttribute, value);
        }
        else {
            ArrayNode result = mapper.createArrayNode();
            List<Object> values = (List<Object>) eObject.eGet(eAttribute);
            for (Object value: values) {
                result.add(toJson(eAttribute, value));
            }
            return result;
        }
    }

    private ValueNode toJson(EAttribute eAttribute, Object value) {
        if (value == null) {
            return NullNode.instance;
        }
        if (value instanceof Float) {
            return FloatNode.valueOf((Float) value);
        }
        if (value instanceof Double) {
            return DoubleNode.valueOf((Double) value);
        }
        if (value instanceof Number) {
            return DecimalNode.valueOf(new BigDecimal(value.toString()));
        }
        if (value instanceof Boolean) {
            return BooleanNode.valueOf((Boolean) value);
        }
        EDataType eDataType = eAttribute.getEAttributeType();
        String image = EcoreUtil.convertToString(eDataType, value);
        return TextNode.valueOf(image);
    }

    private JsonNode toJson(EObject eObject, EReference eReference) {
        if (eReference.isContainer()) return null;
        if (!eReference.isMany()) {
            EObject refObject = (EObject) eObject.eGet(eReference);
            return toJson(eObject, eReference, refObject);
        }
        else {
            ArrayNode result = mapper.createArrayNode();
            List<EObject> values = (List<EObject>) eObject.eGet(eReference);
            for (EObject value: values) {
                result.add(toJson(eObject, eReference, value));
            }
            return result;
        }
    }

    private ObjectNode toJson(EObject base, EReference eReference, EObject refObject) {
        if (eReference.isContainment()) {
            return toJson(refObject, eReference.getEReferenceType());
        }
        else {
            ObjectNode refNode = mapper.createObjectNode();
            if (!refObject.eClass().equals(eReference.getEReferenceType())) {
                refNode.put("eClass", EcoreUtil.getURI(refObject.eClass()).toString());
            }
            URI refURI = EcoreUtil.getURI(refObject).trimQuery()
                    .deresolve(EcoreUtil.getURI(base).trimQuery(),
                            true, true, false);
            refNode.put("$ref", refURI.toString());
            return refNode;
        }
    }

    public ObjectNode toJson(Resource resource) {
        ObjectNode result = mapper.createObjectNode();
        result.put("uri", resource.getURI().toString());
        result.put("timestamp", resource.getTimeStamp());
        ArrayNode contents = result.withArray("contents");
        resource.getContents().stream().forEach(eObject -> contents.add(toJson(eObject)));
        return result;
    }

    public byte[] toBytes(Resource resource) throws JsonProcessingException {
        ObjectNode result = toJson(resource);
        return mapper.writer().writeValueAsBytes(result);
    }

    public String toString(Resource resource) throws JsonProcessingException {
        ObjectNode result = toJson(resource);
        return mapper.writer().writeValueAsString(result);
    }

    public ArrayNode toJson(List<Resource> resources) {
        ArrayNode nodes = mapper.createArrayNode();
        for (Resource resource: resources) {
            nodes.add(toJson(resource));
        }
        return nodes;
    }

    public void fromJson(Resource resource, ObjectNode body) {
        fromJson(resource, body, null);
    }
    public void fromJson(Resource resource, ObjectNode body, URI defaultUri) {
        JsonNode uriNode = body.get("uri");
        URI uri = uriNode == null || uriNode.asText().length() == 0 ? defaultUri : URI.createURI(uriNode.asText());
        if (uri != null) {
            resource.setURI(uri);
        }
        JsonNode timestampNode = body.get("timestamp");
        Long timestamp = timestampNode == null ? null : timestampNode.asLong();
        if (timestamp != null) {
            resource.setTimeStamp(timestamp);
        }
        ArrayNode contents = body.withArray("contents");
        for (JsonNode eObjectNode: contents) {
            EObject eObject = eObjectFromJson(resource, (ObjectNode)eObjectNode, null);
            resource.getContents().add(eObject);
        }
        Map<EObject, Collection<EStructuralFeature.Setting>> crs = EcoreUtil.UnresolvedProxyCrossReferencer.find(resource);
        for (Map.Entry<EObject, Collection<EStructuralFeature.Setting>> entry: crs.entrySet()) {
            InternalEObject internalEObject = (InternalEObject) entry.getKey();
            URI proxyURI = internalEObject.eProxyURI();
            if (proxyURI.isCurrentDocumentReference()) {
                EObject resolved = resource.getEObject(proxyURI.fragment());
                if (resolved != null) {
                    for (EStructuralFeature.Setting setting: entry.getValue()) {
                        EcoreUtil.replace(setting, internalEObject, resolved);
                    }
                }
            }
            else {
                URI relProxy = proxyURI.trimQuery().resolve(resource.getURI().trimQuery(),
                        true).appendQuery(proxyURI.query());
                internalEObject.eSetProxyURI(relProxy);
            }
        }
    }

    public void fromJson(Resource resource, byte[] body) throws IOException {
        ObjectNode node = mapper.reader().createParser(body).readValueAsTree();
        fromJson(resource, node);
    }

    private EObject eObjectFromJson(Resource resource, ObjectNode eObjectNode, EClass eClass) {
        JsonNode eClassNode = eObjectNode.get("eClass");
        if (eClassNode != null) {
            URI classUri = URI.createURI(eClassNode.asText());
            eClass = (EClass) resource.getResourceSet().getEObject(classUri, false);
            Objects.requireNonNull(eClass, ()->"Class not found " + eClassNode.asText());
        }
        EObject eObject = EcoreUtil.create(eClass);
        for (EStructuralFeature sf: eClass.getEAllStructuralFeatures()) {
            if (!sf.isDerived() && !sf.isTransient()) {
                JsonNode valueNode = eObjectNode.get(sf.getName());
                if (valueNode != null) {
                    fromJson(resource, eObject, sf, valueNode);
                }
            }
        }
        String id = eObjectNode.path("_id").asText(null);
        if (id != null) {
            setId(resource, eObject, id);
        }
        return eObject;
    }

    private void fromJson(Resource resource, EObject eObject, EStructuralFeature sf, JsonNode valueNode) {
        if (sf instanceof EAttribute) fromJson(eObject, (EAttribute) sf, valueNode);
        else if (sf instanceof EReference) fromJson(resource, eObject, (EReference) sf, valueNode);
    }

    abstract protected void setId(Resource resource, EObject eObject, String id);

    private void fromJson(Resource resource, EObject eObject, EReference eReference, JsonNode valueNode) {
        if (eReference.isContainer()) return;
        if (!eReference.isMany()) {
            EObject refObject = fromJson(resource, eObject, eReference, (ObjectNode)valueNode);
            eObject.eSet(eReference, refObject);
            ObjectNode objectNode = (ObjectNode)valueNode;
            String id = objectNode.path("_id").asText(null);
            if (id != null) {
                setId(resource, eObject, id);
            }
        }
        else {
            List<EObject> list = (List<EObject>) eObject.eGet(eReference);
            ArrayNode elements = (ArrayNode) valueNode;
            for (JsonNode element: elements) {
                ObjectNode objectNode = (ObjectNode)element;
                EObject refObject = fromJson(resource, eObject, eReference, objectNode);
                list.add(refObject);
                String id = objectNode.path("_id").asText(null);
                if (id != null) {
                    setId(resource, refObject, id);
                }
            }
        }
    }

    private EObject fromJson(Resource resource, EObject eObject, EReference eReference, ObjectNode valueNode) {
        EClass referenceType = eReference.getEReferenceType();
        if (eReference.isContainment()) {
            return eObjectFromJson(resource, valueNode, referenceType);
        }
        else {
            JsonNode eClassNode = valueNode.get("eClass");
            if (eClassNode != null) {
                URI classUri = URI.createURI(eClassNode.asText());
                referenceType = (EClass) resource.getResourceSet().getEObject(classUri, false);
            }
            String ref = valueNode.get("$ref").asText();
            URI refUri = URI.createURI(ref);
            EObject refObject = EcoreUtil.create(referenceType);
            ((InternalEObject)refObject).eSetProxyURI(refUri);
            return refObject;
        }
    }

    private void fromJson(EObject eObject, EAttribute eAttribute, JsonNode valueNode) {
        EDataType eDataType = eAttribute.getEAttributeType();
        if (!eAttribute.isMany()) {
            Object value = EcoreUtil.createFromString(eDataType, valueNode.asText());
            eObject.eSet(eAttribute, value);
        }
        else {
            ArrayNode elements = (ArrayNode) valueNode;
            List contents = (List) eObject.eGet(eAttribute);
            for (JsonNode element: elements) {
                Object value = EcoreUtil.createFromString(eDataType, element.asText());
                contents.add(value);
            }
        }
    }
}
