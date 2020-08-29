package ru.neoflex.emf.restserver;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.annotation.BeforeTestClass;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.util.Assert;
import ru.neoflex.emf.test.Group;
import ru.neoflex.emf.test.TestFactory;
import ru.neoflex.emf.test.TestPackage;

import javax.annotation.PostConstruct;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "db-type=ru.neoflex.emf.gitdb.GitDBFactory",
        "db-name=resttest"
})
class RestserverApplicationTests {
    private static boolean initialized = false;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DBServerSvc dbServerSvc;

    @BeforeTestClass
    public void initializeDB() {
        if (!initialized) {
            initialized = true;
        }
    }

    @Test
    void contextLoads() {
    }

    @PostConstruct
    void init() {
        dbServerSvc.getDbServer().registerEPackage(TestPackage.eINSTANCE);
    }

    @Test
    void createResources() throws Exception {
        Group group = TestFactory.eINSTANCE.createGroup();
        group.setName("masters");
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        Resource resource = resourceSet.createResource(URI.createURI(""));
        resource.getContents().add(group);
        byte[] content = new JsonHelper().toBytes(resource);
        ResultActions resultActions = mockMvc.perform(MockMvcRequestBuilders.post("/emf/resource")
                .content(content).contentType(MediaType.APPLICATION_JSON));
        MvcResult mvcResult = resultActions.andDo(MockMvcResultHandlers.print()).andExpect(status().isOk()).andReturn();
        content = mvcResult.getResponse().getContentAsByteArray();
        resource.unload();
        new JsonHelper().fromJson(resource, content);
        String version = dbServerSvc.getDbServer().getVersion(resource.getURI());
        Assert.notNull(version, "version not null");
    }
}
