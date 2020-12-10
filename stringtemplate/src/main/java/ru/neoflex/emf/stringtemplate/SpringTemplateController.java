package ru.neoflex.emf.stringtemplate;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.util.logging.Logger;

@RestController()
@RequestMapping("/sparksql")
public class SpringTemplateController {
    Logger logger = Logger.getLogger(SpringTemplateController.class.getName());
    final DBServerSvc dbServerSvc;

    public SpringTemplateController(DBServerSvc dbServerSvc) {
        this.dbServerSvc = dbServerSvc;
    }

    @PostConstruct
    void init() {

    }

}
