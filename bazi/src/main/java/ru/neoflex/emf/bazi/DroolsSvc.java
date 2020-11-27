package ru.neoflex.emf.bazi;

import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.io.KieResources;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.DecisionTableConfiguration;
import org.kie.internal.builder.DecisionTableInputType;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.springframework.stereotype.Service;
import org.kie.api.KieBase;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class DroolsSvc {
    private KieContainer kieContainer;
    private boolean debug;
    private final List<Supplier<Collection<Resource>>> resourceFactories = new ArrayList<>();
    private final List<AbstractMap.SimpleEntry<String, Object>> globals = new ArrayList<>();

    @PostConstruct
    void init() {
    }

    @PreDestroy
    void fini() {
        disposeContainer();
    }

    public void disposeContainer() {
        if (kieContainer != null) {
            kieContainer.dispose();
            kieContainer = null;
        }
    }

    public KieBase getKieBase() {
        if (kieContainer == null) {
            KieServices kieServices = KieServices.Factory.get();
            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
            for (Resource resource: getResourcesList()) {
                kieFileSystem.write(resource);
            }
            KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
            kieBuilder.buildAll();
            if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
                throw new RuntimeException("Build Errors:\n" + kieBuilder.getResults().toString());
            }
            KieModule kieModule = kieBuilder.getKieModule();
            kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
        }
        String baseName = kieContainer.getKieBaseNames().stream().findFirst().get();
        return kieContainer.getKieBase(baseName);
    }

    protected Collection<Resource> getResourcesList() {
        List<Resource> resources = new ArrayList<>();
        for (Supplier<Collection<Resource>> factory: resourceFactories) {
            resources.addAll(factory.get());
        }
        return resources;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public List<Supplier<Collection<Resource>>> getResourceFactories() {
        return resourceFactories;
    }

    public KieSession createSession() {
        KieSession kieSession = getKieBase().newKieSession();
        if (debug) {
            KieServices.Factory.get().getLoggers().newConsoleLogger(kieSession);
        }
        for (AbstractMap.SimpleEntry<String, Object> global: globals) {
            kieSession.setGlobal(global.getKey(), global.getValue());
        }
        return kieSession;
    }

    public List<AbstractMap.SimpleEntry<String, Object>> getGlobals() {
        return globals;
    }

    public static KieResources getKieResources() {
        return KieServices.Factory.get().getResources();
    }

    public static Resource setResourceType(String path, ResourceType resourceType, Resource resource) {
        resource.setSourcePath(path);
        if (resourceType == null) {
            resourceType = ResourceType.determineResourceType(path);
        }
        resource.setResourceType(resourceType);
        return resource;
    }

    public static Resource createInputStreamResource(String path, ResourceType resourceType, InputStream is) {
        Resource resource = getKieResources().newInputStreamResource(is);
        return setResourceType(path, resourceType, resource);
    }

    public static Resource createDecisionTableResource(String path, InputStream is, DecisionTableInputType tableInputType) {
        Resource resource = getKieResources().newInputStreamResource(is);
        resource.setResourceType(ResourceType.DTABLE);
        DecisionTableConfiguration resourceConfiguration = KnowledgeBuilderFactory.newDecisionTableConfiguration();
        resourceConfiguration.setInputType(tableInputType);
        resource.setConfiguration(resourceConfiguration);
        return resource;
    }

    public static Resource createByteArrayResource(String path, ResourceType resourceType, byte[] bytes) {
        Resource resource = getKieResources().newByteArrayResource(bytes);
        return setResourceType(path, resourceType, resource);
    }

    public static Resource createClassPathResource(String path, ResourceType resourceType) {
        Resource resource = getKieResources().newClassPathResource(path);
        return setResourceType(path, resourceType, resource);
    }

    public static Collection<Resource> createJarResource(InputStream is) {
        List<Resource> resources = new ArrayList<>();
        MemoryFileSystem mfs = MemoryFileSystem.readFromJar(is);
        for (String path: mfs.getFileNames()) {
            ResourceType resourceType = ResourceType.determineResourceType(path);
            if (resourceType != null || path.endsWith(".class")) {
                byte[] bytes = mfs.getMap().get(path);
                Resource resource = createByteArrayResource(path, resourceType, bytes);
                resources.add(resource);
            }
        }
        return resources;
    }
}
