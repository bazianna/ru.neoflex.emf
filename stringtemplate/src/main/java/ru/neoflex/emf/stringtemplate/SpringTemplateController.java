package ru.neoflex.emf.stringtemplate;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.emf.hron.HbHronSupport;
import ru.neoflex.emf.hron.HronEvaluator;
import ru.neoflex.emf.hron.HronLexer;
import ru.neoflex.emf.hron.HronParser;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.nio.CharBuffer;
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

    @PostMapping(value = "/parseHron", consumes = {"text/plain"})
    public ObjectNode parseHron(@RequestBody String sql) throws Exception {
        CharStream input = CodePointCharStream.fromBuffer(
                CodePointBuffer.withChars(CharBuffer.wrap(sql.toCharArray())));
        HronLexer lexer = new HronLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        HronParser parser = new HronParser(tokens);
        ParseTree tree = parser.resource();
        logger.info(tree.toStringTree(parser));
        //ETreeElement eTree = parseTreeToETree(tree, parser);
        Resource resource = dbServerSvc.getDbServer().createResource();
        HronEvaluator evaluator = new HronEvaluator(resource, new HbHronSupport(dbServerSvc.getDbServer()));
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(evaluator, tree);
        resource.save(null);
        return DBServerSvc.createJsonHelper().toJson(resource);
    }
}
