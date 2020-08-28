package ru.neoflex.emf.restserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.io.IOException;
import java.util.List;

public class JsonHelper {
    JsonMapper mapper = new JsonMapper();

    public ObjectNode toJson(EObject eObject) {
        return toJson(eObject, (EClass) null);
    }

    public ObjectNode toJson(EObject eObject, EClass eClass) {
        ObjectNode result = mapper.createObjectNode();
        if (!eObject.eClass().equals(eClass)) {
            result.put("eClass", getURI(eObject.eClass()).toString());
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

    private TextNode toJson(EAttribute eAttribute, Object value) {
        EDataType eDataType = eAttribute.getEAttributeType();
        String image = EcoreUtil.convertToString(eDataType, value);
        return TextNode.valueOf(image);
    }

    private JsonNode toJson(EObject eObject, EReference eReference) {
        if (eReference.isContainer()) return null;
        if (!eReference.isMany()) {
            EObject refObject = (EObject) eObject.eGet(eReference);
            return toJson(eReference, refObject);
        }
        else {
            ArrayNode result = mapper.createArrayNode();
            List<EObject> values = (List<EObject>) eObject.eGet(eReference);
            for (EObject value: values) {
                result.add(toJson(eReference, value));
            }
            return result;
        }
    }

    private ObjectNode toJson(EReference eReference, EObject refObject) {
        if (eReference.isContainment()) {
            return toJson(refObject, eReference.getEReferenceType());
        }
        else {
            ObjectNode refNode = mapper.createObjectNode();
            if (!refObject.eClass().equals(eReference.getEReferenceType())) {
                refNode.put("eClass", getURI(refObject.eClass()).toString());
            }
            refNode.put("$ref", getURI(refObject).toString());
            return refNode;
        }
    }

    public static URI getURI(EObject eObject) {
        if (eObject instanceof EPackage) {
            URI uri = URI.createURI(((EPackage)eObject).getNsURI());
            return uri.appendFragment(eObject.eResource().getURIFragment(eObject));
        }
        if (eObject instanceof EClass) {
            URI uri = URI.createURI(((EClass)eObject).getEPackage().getNsURI());
            return uri.appendFragment(eObject.eResource().getURIFragment(eObject));
        }
        return EcoreUtil.getURI(eObject);
    }

    public ObjectNode toJson(Resource resource) {
        ObjectNode result = mapper.createObjectNode();
        result.put("uri", resource.getURI().toString());
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
        ArrayNode contents = body.withArray("contents");
        for (JsonNode eObjectNode: contents) {
            EObject eObject = eObjectFromJson(resource.getResourceSet(), (ObjectNode)eObjectNode, null);
            resource.getContents().add(eObject);
        }
    }

    public void fromJson(Resource resource, byte[] body) throws IOException {
        ObjectNode node = mapper.reader().createParser(body).readValueAsTree();
        fromJson(resource, node);
    }

    private EObject eObjectFromJson(ResourceSet rs, ObjectNode eObjectNode, EClass eClass) {
        JsonNode eClassNode = eObjectNode.get("eClass");
        if (eClassNode != null) {
            URI classUri = URI.createURI(eClassNode.asText());
            eClass = (EClass) rs.getEObject(classUri, false);
        }
        EObject eObject = EcoreUtil.create(eClass);
        for (EStructuralFeature sf: eClass.getEAllStructuralFeatures()) {
            if (!sf.isDerived() && !sf.isTransient()) {
                JsonNode valueNode = eObjectNode.get(sf.getName());
                if (valueNode != null) {
                    fromJson(rs, eObject, sf, valueNode);
                }
            }
        }
        return eObject;
    }

    private void fromJson(ResourceSet rs, EObject eObject, EStructuralFeature sf, JsonNode valueNode) {
        if (sf instanceof EAttribute) fromJson(eObject, (EAttribute) sf, valueNode);
        else if (sf instanceof EReference) fromJson(rs, eObject, (EReference) sf, valueNode);
    }

    private void fromJson(ResourceSet rs, EObject eObject, EReference eReference, JsonNode valueNode) {
        if (eReference.isContainer()) return;
        if (!eReference.isMany()) {
            EObject refObject = fromJson(rs, eObject, eReference, (ObjectNode)valueNode);
            eObject.eSet(eReference, refObject);
        }
        else {
            List<EObject> list = (List<EObject>) eObject.eGet(eReference);
            ArrayNode elements = (ArrayNode) valueNode;
            for (JsonNode element: elements) {
                EObject refObject = fromJson(rs, eObject, eReference, (ObjectNode)element);
                list.add(refObject);
            }
        }
    }

    private EObject fromJson(ResourceSet rs, EObject eObject, EReference eReference, ObjectNode valueNode) {
        EClass referenceType = eReference.getEReferenceType();
        if (eReference.isContainment()) {
            return eObjectFromJson(rs, valueNode, referenceType);
        }
        else {
            JsonNode eClassNode = valueNode.get("eClass");
            if (eClassNode != null) {
                URI classUri = URI.createURI(eClassNode.asText());
                referenceType = (EClass) rs.getEObject(classUri, false);
            }
            EObject refObject = EcoreUtil.create(referenceType);
            String ref = valueNode.get("$ref").asText();
            URI refUri = URI.createURI(ref);
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
