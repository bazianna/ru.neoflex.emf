package ru.neoflex.emf.restserver;

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
import ru.neoflex.emf.base.HbResource;
import ru.neoflex.emf.base.HbTransaction;
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
        dbServerSvc.getDbServer().inTransaction(false, HbTransaction::truncate);
        Group group = TestFactory.eINSTANCE.createGroup();
        group.setName("masters");
        dbServerSvc.getDbServer().inTransaction(true, tx -> {
            HbResource resource = tx.createResource(tx.getHbServer().createURI());
            resource.getContents().add(group);
            byte[] content = DBServerSvc.createJsonHelper().toBytes(resource);
            ResultActions resultActions = mockMvc.perform(MockMvcRequestBuilders.post("/emf/resource")
                    .content(content).contentType(MediaType.APPLICATION_JSON));
            MvcResult mvcResult = resultActions.andDo(MockMvcResultHandlers.print()).andExpect(status().isOk()).andReturn();
            byte[] contentResult = mvcResult.getResponse().getContentAsByteArray();
            resource.unload();
            DBServerSvc.createJsonHelper().fromJson(resource, contentResult);
            Assert.isTrue(resource.getTimeStamp() != 0, "version not null");
            return resource;
        });
    }
}
