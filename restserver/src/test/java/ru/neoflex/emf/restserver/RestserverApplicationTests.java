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
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBTransaction;
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
        dbServerSvc.getDbServer().inTransaction(false, DBTransaction::truncate);
        Group group = TestFactory.eINSTANCE.createGroup();
        group.setName("masters");
        dbServerSvc.getDbServer().inTransaction(true, tx -> {
            DBResource resource = tx.createResource(tx.getDbServer().createURI(null));
            resource.getContents().add(group);
            byte[] content = new JsonHelper().toBytes(resource);
            ResultActions resultActions = mockMvc.perform(MockMvcRequestBuilders.post("/emf/resource")
                    .content(content).contentType(MediaType.APPLICATION_JSON));
            MvcResult mvcResult = resultActions.andDo(MockMvcResultHandlers.print()).andExpect(status().isOk()).andReturn();
            byte[] contentResult = mvcResult.getResponse().getContentAsByteArray();
            resource.unload();
            new JsonHelper().fromJson(resource, contentResult);
            Integer version = dbServerSvc.getDbServer().getVersion(resource.getContents().get(0));
            Assert.notNull(version, "version not null");
            return resource;
        });
    }
}
