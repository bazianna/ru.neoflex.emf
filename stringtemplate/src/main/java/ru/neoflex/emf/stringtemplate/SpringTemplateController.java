package ru.neoflex.emf.stringtemplate;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.emf.ecore.resource.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.emf.antlr4.Antlr4Factory;
import ru.neoflex.emf.antlr4.ERule;
import ru.neoflex.emf.antlr4.ETerminal;
import ru.neoflex.emf.antlr4.ETreeElement;
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
        ETreeElement eTree = parseTreeToETree(tree, parser);
        Resource resource = dbServerSvc.getDbServer().createResource();
        resource.getContents().add(eTree);
        resource.save(null);
        return DBServerSvc.createJsonHelper().toJson(resource);
    }

    public ETreeElement parseTreeToETree(ParseTree tree, Parser parser) {
        if (tree instanceof RuleNode) {
            RuleNode node = (RuleNode) tree;
            ERule eNode = Antlr4Factory.eINSTANCE.createERule();
            eNode.setRuleIndex(node.getRuleContext().getRuleIndex());
            eNode.setRuleName(parser.getRuleNames()[eNode.getRuleIndex()]);
            eNode.setAltNumber(node.getRuleContext().getAltNumber());
            eNode.setLabel(node.getClass().getSimpleName().replaceAll("Context$",""));
            for (int i = 0; i < node.getChildCount(); ++i) {
                ParseTree child = node.getChild(i);
                ETreeElement eTreeElement = parseTreeToETree(child, parser);
                eNode.getChildren().add(eTreeElement);
            }
            return eNode;
        }
        if (tree instanceof TerminalNode) {
            TerminalNode node = (TerminalNode) tree;
            ETerminal eNode = tree instanceof ErrorNode ? Antlr4Factory.eINSTANCE.createEError() : Antlr4Factory.eINSTANCE.createETerminal();
            eNode.setTokenType(node.getSymbol().getType());
            eNode.setTypeName(parser.getVocabulary().getDisplayName(eNode.getTokenType()));
            eNode.setText(node.getText());
            return eNode;
        }
        throw new IllegalArgumentException("Unknown node: " + tree.toStringTree(parser));
    }

}
