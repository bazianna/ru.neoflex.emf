package ru.neoflex.emf.stringtemplate;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CodePointBuffer;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import ru.neoflex.emf.hron.*;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

@SpringBootTest(properties = {
        "db-name=hrontest"
}, classes = {ru.neoflex.emf.restserver.DBServerSvc.class})
public class HronTests {
    Logger logger = Logger.getLogger(HronTests.class.getName());

    @Autowired
    private DBServerSvc dbServerSvc;

    @PostConstruct
    void init() {
        dbServerSvc.getDbServer().registerEPackage(StringtemplatePackage.eINSTANCE);
    }

    @Test
    public void loadMyClass() throws Exception {
        ClassPathResource myClassResource = new ClassPathResource("myClass.hron");
        String code = new String(Files.readAllBytes(myClassResource.getFile().toPath()), StandardCharsets.UTF_8);
        CharStream input = CodePointCharStream.fromBuffer(
                CodePointBuffer.withChars(CharBuffer.wrap(code.toCharArray())));
        HronLexer lexer = new HronLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        HronParser parser = new HronParser(tokens);
        ParseTree tree = parser.resource();
        Resource resource = dbServerSvc.getDbServer().createResource();
        HronEvaluator evaluator = new HronEvaluator(resource, new HbHronSupport(dbServerSvc.getDbServer()));
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(evaluator, tree);
        evaluator.setPhase(HronEvaluator.Phase.NONCONTAINMENT);
        walker.walk(evaluator, tree);
//        resource.save(null);
        String s = Hron.export(resource);
        logger.info(s);
    }
}