package ru.neoflex.emf.restserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController()
@RequestMapping("/emf")
public class EMFController {
    @Autowired
    DBServerSvc dbServerSvc;

    @GetMapping("/resource")
    JsonNode getResource(String id) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(true, tx -> {
            ResourceSet rs = tx.createResourceSet();
            URI uri = tx.getDbServer().createURI(id);
            Resource resource = rs.getResource(uri, true);
            return new JsonHelper().toJson(resource);
        });
    }

    @PutMapping("/resource")
    JsonNode putResource(String id, @RequestBody ObjectNode body) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            ResourceSet rs = tx.createResourceSet();
            URI uri = tx.getDbServer().createURI(id);
            Resource resource = rs.createResource(uri);
            new JsonHelper().fromJson(resource, body);
            resource.save(null);
            return new JsonHelper().toJson(resource);
        });
    }

    @PostMapping("/resource")
    JsonNode postResource(@RequestBody ObjectNode body) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            ResourceSet rs = tx.createResourceSet();
            JsonNode uriNode = body.get("uri");
            URI uri = uriNode == null ? tx.getDbServer().createURI("") : URI.createURI(uriNode.asText());
            Resource resource = rs.createResource(uri);
            new JsonHelper().fromJson(resource, body);
            resource.save(null);
            return new JsonHelper().toJson(resource);
        });
    }

    @DeleteMapping("/resource")
    void deleteResource(String id) throws Exception {
        dbServerSvc.getDbServer().inTransaction(false, tx -> {
            URI uri = tx.getDbServer().createURI(id);
            tx.delete(uri);
            return null;
        });
    }

    @GetMapping("/findall")
    JsonNode getAll() throws Exception {
        return dbServerSvc.getDbServer().inTransaction(true, tx -> {
            ResourceSet rs = tx.createResourceSet();
            List<Resource> resources = tx.findAll(rs).collect(Collectors.toList());
            return new JsonHelper().toJson(resources);
        });
    }
}
