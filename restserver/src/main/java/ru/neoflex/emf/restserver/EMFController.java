package ru.neoflex.emf.restserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.JSONPObject;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            new JsonHelper().fromJson(resource, body, uri);
            resource.save(null);
            return new JsonHelper().toJson(resource);
        });
    }

    @PostMapping("/resource")
    JsonNode postResource(@RequestBody ObjectNode body) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            ResourceSet rs = tx.createResourceSet();
            URI uri = tx.getDbServer().createURI("");
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

    @GetMapping("/find")
    JsonNode find(
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String classUri,
            @RequestParam(required = false) String qName,
            @RequestParam(required = false) String filter) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(true, tx -> {
            ResourceSet rs = tx.createResourceSet();
            Stream<Resource> stream;
            if (!StringUtils.isEmpty(path)) {
                stream = tx.findByPath(rs, path);
            }
            else if (StringUtils.isEmpty(classUri)) {
                stream = tx.findAll(rs);
            }
            else {
                EClass eClass = (EClass) rs.getEObject(URI.createURI(classUri), false);
                if (StringUtils.isEmpty(qName)) {
                    stream = tx.findByClass(rs, eClass);
                }
                else {
                    stream = tx.findByClassAndQName(rs, eClass, qName);
                }
            }
            if (StringUtils.isNoneEmpty(filter)) {
                GroovyShell shell = new GroovyShell();
                Script script = shell.parse(filter);
                stream = stream.filter(resource -> {
                    Binding binding = new Binding();
                    binding.setProperty("resource", resource);
                    script.setBinding(binding);
                    return Boolean.TRUE.equals(script.run());
                });
            }
            List<Resource> resources = stream.collect(Collectors.toList());
            return new JsonHelper().toJson(resources);
        });
    }

    @PostMapping("/xcore")
    public ObjectNode uploadXcore(@RequestParam("file") MultipartFile file) throws IOException {
        Resource resource = dbServerSvc.uploadXcore(file.getInputStream(), file.getOriginalFilename());
        return new JsonHelper().toJson(resource);
    }

    @PostMapping("/ecore")
    public ObjectNode uploadEcore(@RequestParam("file") MultipartFile file) throws IOException {
        Resource resource = dbServerSvc.uploadEcore(file.getInputStream(), file.getOriginalFilename());
        return new JsonHelper().toJson(resource);
    }

    @GetMapping("/ecore")
    public ResponseEntity<ByteArrayResource> downloadEcore(@RequestParam("nsUri") String nsUri) {
        byte[] content = dbServerSvc.downloadEcore(nsUri);
        ByteArrayResource resource = new ByteArrayResource(content);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nsUri.replaceAll("\\W+", "_") + ".ecore");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.EXPIRES, "0");
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(content.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
