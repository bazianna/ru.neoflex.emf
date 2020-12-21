package ru.neoflex.emf.restserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.hibernate.query.Query;
import org.hibernate.query.QueryParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.emf.hron.HronResourceSet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController()
@RequestMapping("/emf")
public class EMFController {
    @Autowired
    DBServerSvc dbServerSvc;

    JsonHelper jsonHelper = DBServerSvc.createJsonHelper();

    @GetMapping("/resource")
    JsonNode getResource(Long id) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            URI uri = tx.getHbServer().createURI(id);
            Resource resource = rs.getResource(uri, true);
            return jsonHelper.toJson(resource);
        });
    }

    @PutMapping("/resource")
    JsonNode putResource(Long id, Long version, @RequestBody ObjectNode body) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();
            URI uri = tx.getHbServer().createURI(id, version);
            Resource resource = rs.createResource(uri);
            jsonHelper.fromJson(resource, body, uri);
            resource.save(null);
            return jsonHelper.toJson(resource);
        });
    }

    @PostMapping("/resource")
    JsonNode postResource(@RequestBody ObjectNode body) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();
            URI uri = tx.getHbServer().createURI();
            Resource resource = rs.createResource(uri);
            jsonHelper.fromJson(resource, body);
            resource.save(null);
            return jsonHelper.toJson(resource);
        });
    }

    @DeleteMapping("/resource")
    void deleteResource(Long id, Long version) throws Exception {
        dbServerSvc.getDbServer().inTransaction(false, tx -> {
            URI uri = tx.getHbServer().createURI(id, version);
            tx.delete(uri);
            return null;
        });
    }

    @GetMapping("/find")
    JsonNode find(
            @RequestParam(required = false) String classUri,
            @RequestParam(required = false) String qName,
            @RequestParam(required = false) String filter) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            Resource resource;
            if (StringUtils.isEmpty(classUri)) {
                resource = dbServerSvc.getDbServer().findAll(rs);
            }
            else {
                EClass eClass = (EClass) rs.getEObject(URI.createURI(classUri), false);
                if (StringUtils.isEmpty(qName)) {
                    resource = dbServerSvc.getDbServer().findBy(rs, eClass);
                }
                else {
                    resource = dbServerSvc.getDbServer().findBy(rs, eClass, qName);
                }
            }
            if (StringUtils.isNoneEmpty(filter)) {
                GroovyShell shell = new GroovyShell();
                Script script = shell.parse(filter);
                resource.getContents().removeIf(eObject -> {
                    Binding binding = new Binding();
                    binding.setProperty("eObject", eObject);
                    script.setBinding(binding);
                    return !Boolean.TRUE.equals(script.run());
                });
            }
            return jsonHelper.toJson(resource);
        });
    }

    @PostMapping("/xcore")
    public ObjectNode uploadXcore(@RequestParam("file") MultipartFile file) throws IOException {
        Resource resource = dbServerSvc.uploadXcore(file.getInputStream(), file.getOriginalFilename());
        return jsonHelper.toJson(resource);
    }

    @PostMapping("/ecore")
    public ObjectNode uploadEcore(@RequestParam("file") MultipartFile file) throws IOException {
        Resource resource = dbServerSvc.uploadEcore(file.getInputStream(), file.getOriginalFilename());
        return jsonHelper.toJson(resource);
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

    @GetMapping("/query")
    public List query(@RequestParam("sql") String sql) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(true, tx -> {
            return tx.getSession().createQuery(sql).list();
        });
    }

    @PostMapping(value = "/query", consumes = {"text/plain"})
    public List<JsonNode> query(@RequestParam(required = false) List<String> params, @RequestBody String sql) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(true, tx -> {
            Query query = tx.getSession().createQuery(sql);
            for (QueryParameter parameter: query.getParameterMetadata().getPositionalParameters()) {
                query.setParameter(parameter, params.get(parameter.getPosition() - 1));
            }
            JsonMapper mapper = new JsonMapper();
            Stream<JsonNode> nodeStream = query.list().stream().map(mapper::valueToTree);
            return nodeStream.collect(Collectors.toList());
        });
    }

    @PostMapping(value = "/queryObjects", consumes = {"text/plain"})
    public JsonNode queryObjects(@RequestBody String sql) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(true, tx -> {
            ResourceSet rs = tx.createResourceSet();
            Resource resource = rs.createResource(dbServerSvc.getDbServer().createURI(sql));
            resource.load(null);
            return jsonHelper.toJson(resource);
        });
    }

    @GetMapping(value = "/hron", produces = {"text/plain"})
    byte[] getHron(Long id) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            URI uri = tx.getHbServer().createURI(id);
            Resource resource = rs.getResource(uri, true);
            HronResourceSet hrs = new HronResourceSet(new HbHronSupport(dbServerSvc.getDbServer(), rs));
            URI huri = URI.createURI(String.format("%d.hron", id));
            Resource hResource = hrs.createResource(huri);
            hResource.getContents().addAll(EcoreUtil.copyAll(resource.getContents()));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            hResource.save(bos, null);
            return bos.toByteArray();
        });
    }

    @PostMapping(value = "/resource", consumes = {"text/plain"})
    JsonNode postHron(@RequestBody byte[] body) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();
            HronResourceSet hrs = new HronResourceSet(new HbHronSupport(dbServerSvc.getDbServer(), rs));
            URI huri = URI.createURI("body.hron");
            Resource hResource = hrs.createResource(huri);
            ByteArrayInputStream bis = new ByteArrayInputStream(body);
            hResource.load(bis, null);
            hrs.resolveAllReferences();
            List<Long> ids = hResource.getContents().stream().map(eObject -> {
                String name = dbServerSvc.getDbServer().getQName(eObject);
                if (name == null) return null;
                Resource r = dbServerSvc.getDbServer().findBy(eObject.eClass(), name);
                if (r.getContents().size() == 0) return null;
                Long id = dbServerSvc.getDbServer().getId(r.getContents().get(0));
                return id;
            }).collect(Collectors.toList());
            URI uri = tx.getHbServer().createURI();
            Resource resource = rs.createResource(uri);
            List<EObject> newContent = new ArrayList<>(EcoreUtil.copyAll(hResource.getContents()));
            for (int i = 0; i < newContent.size(); ++i) {
                Long id = ids.get(i);
                if (id != null) {
                    dbServerSvc.getDbServer().setId(newContent.get(i), id);
                }
            }
            resource.getContents().addAll(newContent);
            resource.save(null);
            return jsonHelper.toJson(resource);
        });
    }
}
