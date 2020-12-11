package ru.neoflex.emf.stringtemplate;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.emf.ecore.resource.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.emf.antlr4.Antlr4Factory;
import ru.neoflex.emf.antlr4.ETreeElement;
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

    @PostMapping(value = "/parse", consumes = {"text/plain"})
    public ObjectNode parse(@RequestBody String sql) throws Exception {
        CharStream input = CodePointCharStream.fromBuffer(
                CodePointBuffer.withChars(CharBuffer.wrap(sql.toCharArray())));
        ArrayInitLexer lexer = new ArrayInitLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ArrayInitParser parser = new ArrayInitParser(tokens);
        ParseTree tree = parser.init();
        logger.info(tree.toStringTree(parser));
        ETreeElement eTree = parseTreeToETree(tree, parser);
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            Resource resource = tx.createResource();
            resource.getContents().add(eTree);
            resource.save(null);
            return DBServerSvc.createJsonHelper().toJson(resource);
        });
    }

    public ETreeElement parseTreeToETree(ParseTree tree, Parser parser) {
        return Antlr4Factory.eINSTANCE.createETerminal();
    }

}
